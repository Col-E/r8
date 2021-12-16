// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult.SuccessfulFieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.FieldGet;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Eliminate redundant field loads.
 *
 * <p>Simple algorithm that goes through all blocks in one pass in topological order and propagates
 * active field sets across control-flow edges where the target has only one predecessor.
 */
public class RedundantFieldLoadAndStoreElimination {

  private static final int MAX_CAPACITY = 10000;
  private static final int MIN_CAPACITY_PER_BLOCK = 50;

  private final AppView<?> appView;
  private final ProgramMethod method;
  private final IRCode code;
  private final int maxCapacityPerBlock;
  private final boolean release;

  // Values that may require type propagation.
  private final Set<Value> affectedValues = Sets.newIdentityHashSet();

  // Maps keeping track of fields that have an already loaded value at basic block entry.
  private final BlockStates activeStates = new BlockStates();

  // Maps keeping track of fields with already loaded values for the current block during
  // elimination.
  private BlockState activeState;

  private final Map<BasicBlock, Set<Instruction>> instructionsToRemove = new IdentityHashMap<>();

  public RedundantFieldLoadAndStoreElimination(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.method = code.context();
    this.code = code;
    this.maxCapacityPerBlock = Math.max(MIN_CAPACITY_PER_BLOCK, MAX_CAPACITY / code.blocks.size());
    this.release = !appView.options().debug;
  }

  public static boolean shouldRun(AppView<?> appView, IRCode code) {
    return appView.options().enableRedundantFieldLoadElimination
        && (code.metadata().mayHaveFieldInstruction() || code.metadata().mayHaveInitClass());
  }

  private interface FieldValue {

    default ExistingValue asExistingValue() {
      return null;
    }

    void eliminateRedundantRead(InstructionListIterator it, FieldInstruction redundant);
  }

  private class ExistingValue implements FieldValue {

    private final Value value;

    private ExistingValue(Value value) {
      this.value = value;
    }

    @Override
    public ExistingValue asExistingValue() {
      return this;
    }

    @Override
    public void eliminateRedundantRead(InstructionListIterator it, FieldInstruction redundant) {
      affectedValues.addAll(redundant.value().affectedValues());
      redundant.value().replaceUsers(value);
      it.removeOrReplaceByDebugLocalRead();
      value.uniquePhiUsers().forEach(Phi::removeTrivialPhi);
    }

    public Value getValue() {
      return value;
    }

    @Override
    public String toString() {
      return "ExistingValue(v" + value.getNumber() + ")";
    }
  }

  private class MaterializableValue implements FieldValue {

    private final SingleValue value;

    private MaterializableValue(SingleValue value) {
      assert value.isMaterializableInContext(appView.withLiveness(), method);
      this.value = value;
    }

    @Override
    public void eliminateRedundantRead(InstructionListIterator it, FieldInstruction redundant) {
      affectedValues.addAll(redundant.value().affectedValues());
      it.replaceCurrentInstruction(
          value.createMaterializingInstruction(appView.withClassHierarchy(), code, redundant));
    }
  }

  private static class FieldAndObject {
    private final DexField field;
    private final Value object;

    private FieldAndObject(DexField field, Value receiver) {
      assert receiver == receiver.getAliasedValue();
      this.field = field;
      this.object = receiver;
    }

    @Override
    public int hashCode() {
      return field.hashCode() * 7 + object.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof FieldAndObject)) {
        return false;
      }
      FieldAndObject o = (FieldAndObject) other;
      return o.object == object && o.field == field;
    }
  }

  public boolean isFinal(DexClassAndField field) {
    if (field.isProgramField()) {
      // Treat this field as being final if it is declared final or we have determined a constant
      // value for it.
      return field.getDefinition().isFinal()
          || field.getDefinition().getOptimizationInfo().getAbstractValue().isSingleValue();
    }
    return appView.libraryMethodOptimizer().isFinalLibraryField(field.getDefinition());
  }

  private DexClassAndField resolveField(DexField field) {
    if (appView.enableWholeProgramOptimizations()) {
      SuccessfulFieldResolutionResult resolutionResult =
          appView.appInfo().withLiveness().resolveField(field).asSuccessfulResolution();
      return resolutionResult != null ? resolutionResult.getResolutionPair() : null;
    }
    if (field.getHolderType() == method.getHolderType()) {
      return method.getHolder().lookupProgramField(field);
    }
    return null;
  }

  public void run() {
    Reference2IntMap<BasicBlock> pendingNormalSuccessors = new Reference2IntOpenHashMap<>();
    for (BasicBlock block : code.blocks) {
      if (!block.hasUniqueNormalSuccessor()) {
        pendingNormalSuccessors.put(block, block.numberOfNormalSuccessors());
      }
    }

    AssumeRemover assumeRemover = new AssumeRemover(appView, code, affectedValues);
    for (BasicBlock head : code.topologicallySortedBlocks()) {
      if (head.hasUniquePredecessor() && head.getUniquePredecessor().hasUniqueNormalSuccessor()) {
        // Already visited.
        continue;
      }
      activeState = activeStates.computeActiveStateOnBlockEntry(head, maxCapacityPerBlock);
      activeStates.removeDeadBlockExitStates(head, pendingNormalSuccessors);
      BasicBlock block = head;
      BasicBlock end = null;
      do {
        InstructionListIterator it = block.listIterator(code);
        while (it.hasNext()) {
          Instruction instruction = it.next();
          if (instruction.isFieldInstruction()) {
            DexField reference = instruction.asFieldInstruction().getField();
            DexClassAndField field = resolveField(reference);
            if (field == null || field.getDefinition().isVolatile()) {
              killAllNonFinalActiveFields();
              continue;
            }

            if (instruction.isInstanceGet()) {
              handleInstanceGet(it, instruction.asInstanceGet(), field, assumeRemover);
            } else if (instruction.isInstancePut()) {
              handleInstancePut(instruction.asInstancePut(), field);
            } else if (instruction.isStaticGet()) {
              handleStaticGet(it, instruction.asStaticGet(), field, assumeRemover);
            } else if (instruction.isStaticPut()) {
              handleStaticPut(instruction.asStaticPut(), field);
            }
          } else if (instruction.isAssume()) {
            assumeRemover.removeIfMarked(instruction.asAssume(), it);
          } else if (instruction.isInitClass()) {
            handleInitClass(it, instruction.asInitClass());
          } else if (instruction.isMonitor()) {
            if (instruction.asMonitor().isEnter()) {
              killAllNonFinalActiveFields();
            }
          } else if (instruction.isInvokeDirect()) {
            handleInvokeDirect(instruction.asInvokeDirect());
          } else if (instruction.isInvokeMethod() || instruction.isInvokeCustom()) {
            killAllNonFinalActiveFields();
          } else if (instruction.isNewInstance()) {
            NewInstance newInstance = instruction.asNewInstance();
            if (newInstance.clazz.classInitializationMayHaveSideEffectsInContext(appView, method)) {
              killAllNonFinalActiveFields();
            }
          } else {
            // If the current instruction could trigger a method invocation, it could also cause
            // field values to change. In that case, it must be handled above.
            assert !instruction.instructionMayTriggerMethodInvocation(appView, method);

            // Clear the field writes.
            if (instruction.instructionInstanceCanThrow(appView, method)) {
              activeState.clearMostRecentFieldWrites();
            }

            // If this assertion fails for a new instruction we need to determine if that
            // instruction has side-effects that can change the value of fields. If so, it must be
            // handled above. If not, it can be safely added to the assert.
            assert instruction.isArgument()
                    || instruction.isArrayGet()
                    || instruction.isArrayLength()
                    || instruction.isArrayPut()
                    || instruction.isAssume()
                    || instruction.isBinop()
                    || instruction.isCheckCast()
                    || instruction.isConstClass()
                    || instruction.isConstMethodHandle()
                    || instruction.isConstMethodType()
                    || instruction.isConstNumber()
                    || instruction.isConstString()
                    || instruction.isDebugInstruction()
                    || instruction.isDexItemBasedConstString()
                    || instruction.isGoto()
                    || instruction.isIf()
                    || instruction.isInstanceOf()
                    || instruction.isInvokeMultiNewArray()
                    || instruction.isInvokeNewArray()
                    || instruction.isMoveException()
                    || instruction.isNewArrayEmpty()
                    || instruction.isNewArrayFilledData()
                    || instruction.isReturn()
                    || instruction.isSwitch()
                    || instruction.isThrow()
                    || instruction.isUnop()
                    || instruction.isRecordFieldValues()
                : "Unexpected instruction of type " + instruction.getClass().getTypeName();
          }
        }
        if (block.hasUniqueNormalSuccessorWithUniquePredecessor()) {
          block = block.getUniqueNormalSuccessor();
        } else {
          end = block;
          block = null;
        }
      } while (block != null);
      assert end != null;
      activeStates.recordActiveStateOnBlockExit(end, activeState);
    }
    processInstructionsToRemove();
    assumeRemover.removeMarkedInstructions().finish();
    assert code.isConsistentSSA();
  }

  private void processInstructionsToRemove() {
    instructionsToRemove.forEach(
        (block, instructionsToRemoveInBlock) -> {
          assert instructionsToRemoveInBlock.stream()
              .allMatch(instruction -> instruction.getBlock() == block);
          InstructionListIterator instructionIterator = block.listIterator(code);
          while (instructionIterator.hasNext()) {
            Instruction instruction = instructionIterator.next();
            assert !instruction.isJumpInstruction();
            if (instructionsToRemoveInBlock.contains(instruction)) {
              instructionIterator.removeOrReplaceByDebugLocalRead();
              instructionsToRemoveInBlock.remove(instruction);
              if (instructionsToRemoveInBlock.isEmpty()) {
                return;
              }
            }
          }
        });
  }

  private boolean verifyWasInstanceInitializer() {
    VerticallyMergedClasses verticallyMergedClasses = appView.verticallyMergedClasses();
    assert verticallyMergedClasses != null;
    assert verticallyMergedClasses.isMergeTarget(method.getHolderType())
        || appView.horizontallyMergedClasses().isMergeTarget(method.getHolderType());
    assert appView
        .dexItemFactory()
        .isConstructor(appView.graphLens().getOriginalMethodSignature(method.getReference()));
    assert method.getDefinition().getOptimizationInfo().forceInline();
    return true;
  }

  private void handleInvokeDirect(InvokeDirect invoke) {
    if (!appView.enableWholeProgramOptimizations()) {
      killAllNonFinalActiveFields();
      return;
    }

    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, method);
    if (singleTarget == null || !singleTarget.getDefinition().isInstanceInitializer()) {
      killAllNonFinalActiveFields();
      return;
    }

    InstanceInitializerInfo instanceInitializerInfo =
        singleTarget.getDefinition().getOptimizationInfo().getInstanceInitializerInfo(invoke);
    if (instanceInitializerInfo.mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
      killAllNonFinalActiveFields();
    }

    InstanceFieldInitializationInfoCollection fieldInitializationInfos =
        instanceInitializerInfo.fieldInitializationInfos();
    fieldInitializationInfos.forEachWithDeterministicOrder(
        appView,
        (field, info) -> {
          if (!appView.appInfo().withLiveness().mayPropagateValueFor(field.getReference())) {
            return;
          }
          if (info.isArgumentInitializationInfo()) {
            Value value =
                invoke.getArgument(info.asArgumentInitializationInfo().getArgumentIndex());
            Value object = invoke.getReceiver().getAliasedValue();
            FieldAndObject fieldAndObject = new FieldAndObject(field.getReference(), object);
            if (field.isFinal()) {
              activeState.putFinalInstanceField(fieldAndObject, new ExistingValue(value));
            } else {
              activeState.putNonFinalInstanceField(fieldAndObject, new ExistingValue(value));
            }
          } else if (info.isSingleValue()) {
            SingleValue value = info.asSingleValue();
            if (value.isMaterializableInContext(appView.withLiveness(), method)) {
              Value object = invoke.getReceiver().getAliasedValue();
              FieldAndObject fieldAndObject = new FieldAndObject(field.getReference(), object);
              if (field.isFinal()) {
                activeState.putFinalInstanceField(fieldAndObject, new MaterializableValue(value));
              } else {
                activeState.putNonFinalInstanceField(
                    fieldAndObject, new MaterializableValue(value));
              }
            }
          } else {
            assert info.isTypeInitializationInfo();
          }
        });
  }

  private void handleInitClass(InstructionListIterator instructionIterator, InitClass initClass) {
    assert !initClass.outValue().hasAnyUsers();

    killNonFinalActiveFields(initClass);

    // If the instruction can throw, we can't use any previous field stores for store-after-store
    // elimination.
    if (initClass.instructionInstanceCanThrow(appView, method)) {
      activeState.clearMostRecentFieldWrites();
    }

    DexType clazz = initClass.getClassValue();
    if (!activeState.markClassAsInitialized(clazz)) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private void handleInstanceGet(
      InstructionListIterator it,
      InstanceGet instanceGet,
      DexClassAndField field,
      AssumeRemover assumeRemover) {
    if (instanceGet.outValue().hasLocalInfo()) {
      clearMostRecentInstanceFieldWrite(instanceGet, field);
      return;
    }

    Value object = instanceGet.object().getAliasedValue();
    FieldAndObject fieldAndObject = new FieldAndObject(field.getReference(), object);
    FieldValue replacement = activeState.getInstanceFieldValue(fieldAndObject);
    if (replacement != null) {
      markAssumeDynamicTypeUsersForRemoval(instanceGet, replacement, assumeRemover);
      replacement.eliminateRedundantRead(it, instanceGet);
      return;
    }

    activeState.putNonFinalInstanceField(fieldAndObject, new ExistingValue(instanceGet.value()));
    clearMostRecentInstanceFieldWrite(instanceGet, field);
  }

  private void clearMostRecentInstanceFieldWrite(InstanceGet instanceGet, DexClassAndField field) {
    // If the instruction can throw, we need to clear all most-recent-writes, since subsequent field
    // writes (if any) are not guaranteed to be executed.
    if (instanceGet.instructionInstanceCanThrow(appView, method)) {
      activeState.clearMostRecentFieldWrites();
    } else {
      activeState.clearMostRecentInstanceFieldWrite(field.getReference());
    }
  }

  private void markAssumeDynamicTypeUsersForRemoval(
      FieldGet fieldGet, FieldValue replacement, AssumeRemover assumeRemover) {
    ExistingValue existingValue = replacement.asExistingValue();
    if (existingValue == null
        || !existingValue
            .getValue()
            .isDefinedByInstructionSatisfying(
                definition ->
                    definition.isFieldGet()
                        && definition.asFieldGet().getField().getType()
                            == fieldGet.getField().getType())) {
      assumeRemover.markAssumeDynamicTypeUsersForRemoval(fieldGet.outValue());
    }
  }

  private void handleInstancePut(InstancePut instancePut, DexClassAndField field) {
    // An instance-put instruction can potentially write the given field on all objects because of
    // aliases.
    activeState.removeNonFinalInstanceFields(field.getReference());

    // If the instruction can throw, we can't use any previous field stores for store-after-store
    // elimination.
    if (instancePut.instructionInstanceCanThrow(appView, method)) {
      activeState.clearMostRecentFieldWrites();
    }

    // Update the value of the field to allow redundant load elimination.
    Value object = instancePut.object().getAliasedValue();
    FieldAndObject fieldAndObject = new FieldAndObject(field.getReference(), object);
    ExistingValue value = new ExistingValue(instancePut.value());
    if (isFinal(field)) {
      assert !field.getDefinition().isFinal()
          || method.getDefinition().isInstanceInitializer()
          || verifyWasInstanceInitializer();
      activeState.putFinalInstanceField(fieldAndObject, value);
    } else {
      activeState.putNonFinalInstanceField(fieldAndObject, value);

      // Record that this field is now most recently written by the current instruction.
      if (release) {
        InstancePut mostRecentInstanceFieldWrite =
            activeState.putMostRecentInstanceFieldWrite(fieldAndObject, instancePut);
        if (mostRecentInstanceFieldWrite != null) {
          instructionsToRemove
              .computeIfAbsent(
                  mostRecentInstanceFieldWrite.getBlock(), ignoreKey(Sets::newIdentityHashSet))
              .add(mostRecentInstanceFieldWrite);
        }
      }
    }
  }

  private void handleStaticGet(
      InstructionListIterator instructionIterator,
      StaticGet staticGet,
      DexClassAndField field,
      AssumeRemover assumeRemover) {
    if (staticGet.outValue().hasLocalInfo()) {
      killNonFinalActiveFields(staticGet);
      clearMostRecentStaticFieldWrite(staticGet, field);
      return;
    }

    FieldValue replacement = activeState.getStaticFieldValue(field.getReference());
    if (replacement != null) {
      markAssumeDynamicTypeUsersForRemoval(staticGet, replacement, assumeRemover);
      replacement.eliminateRedundantRead(instructionIterator, staticGet);
      return;
    }

    // A field get on a different class can cause <clinit> to run and change static field values.
    killNonFinalActiveFields(staticGet);
    clearMostRecentStaticFieldWrite(staticGet, field);

    FieldValue value = new ExistingValue(staticGet.value());
    if (isFinal(field)) {
      activeState.putFinalStaticField(field.getReference(), value);
    } else {
      activeState.putNonFinalStaticField(field.getReference(), value);
    }

    if (appView.hasLiveness()) {
      SingleFieldValue singleFieldValue =
          field.getDefinition().getOptimizationInfo().getAbstractValue().asSingleFieldValue();
      if (singleFieldValue != null) {
        applyObjectState(staticGet.outValue(), singleFieldValue.getObjectState());
      }
    }
  }

  private void clearMostRecentStaticFieldWrite(StaticGet staticGet, DexClassAndField field) {
    // If the instruction can throw, we need to clear all most-recent-writes, since subsequent field
    // writes (if any) are not guaranteed to be executed.
    if (staticGet.instructionInstanceCanThrow(appView, method)) {
      activeState.clearMostRecentFieldWrites();
    } else {
      activeState.clearMostRecentStaticFieldWrite(field.getReference());
    }
  }

  private void handleStaticPut(StaticPut staticPut, DexClassAndField field) {
    // A field put on a different class can cause <clinit> to run and change static field values.
    killNonFinalActiveFields(staticPut);

    // If the instruction can throw, we can't use any previous field stores for store-after-store
    // elimination.
    if (staticPut.instructionInstanceCanThrow(appView, method)) {
      activeState.clearMostRecentFieldWrites();
    }

    ExistingValue value = new ExistingValue(staticPut.value());
    if (isFinal(field)) {
      assert appView.checkForTesting(
          () -> !field.getDefinition().isFinal() || method.getDefinition().isClassInitializer());
      activeState.putFinalStaticField(field.getReference(), value);
    } else {
      activeState.putNonFinalStaticField(field.getReference(), value);

      if (release) {
        StaticPut mostRecentStaticFieldWrite =
            activeState.putMostRecentStaticFieldWrite(field.getReference(), staticPut);
        if (mostRecentStaticFieldWrite != null) {
          instructionsToRemove
              .computeIfAbsent(
                  mostRecentStaticFieldWrite.getBlock(), ignoreKey(Sets::newIdentityHashSet))
              .add(mostRecentStaticFieldWrite);
        }
      }
    }
  }

  private void applyObjectState(Value value, ObjectState objectState) {
    objectState.forEachAbstractFieldValue(
        (field, fieldValue) -> {
          if (appView.appInfoWithLiveness().mayPropagateValueFor(field)
              && fieldValue.isSingleValue()) {
            SingleValue singleFieldValue = fieldValue.asSingleValue();
            if (singleFieldValue.isMaterializableInContext(appView.withLiveness(), method)) {
              activeState.putFinalInstanceField(
                  new FieldAndObject(field, value), new MaterializableValue(singleFieldValue));
            }
          }
        });
  }

  private void killAllNonFinalActiveFields() {
    activeState.clearNonFinalInstanceFields();
    activeState.clearNonFinalStaticFields();
    activeState.clearMostRecentFieldWrites();
  }

  private void killNonFinalActiveFields(Instruction instruction) {
    assert instruction.isInitClass() || instruction.isStaticFieldInstruction();
    if (instruction.isStaticPut()) {
      if (instruction.instructionMayTriggerMethodInvocation(appView, method)) {
        // Accessing a static field on a different object could cause <clinit> to run which
        // could modify any static field on any other object.
        activeState.clearNonFinalStaticFields();
        activeState.clearMostRecentFieldWrites();
      } else {
        activeState.removeNonFinalStaticField(instruction.asStaticPut().getField());
      }
    } else if (instruction.isInitClass() || instruction.isStaticGet()) {
      if (instruction.instructionMayTriggerMethodInvocation(appView, method)) {
        // Accessing a static field on a different object could cause <clinit> to run which
        // could modify any static field on any other object.
        activeState.clearNonFinalStaticFields();
        activeState.clearMostRecentFieldWrites();
      }
    } else if (instruction.isInstanceGet()) {
      throw new Unreachable();
    }
  }

  static class BlockStates {

    // Maps keeping track of fields that have an already loaded value at basic block entry.
    private final LinkedHashMap<BasicBlock, BlockState> activeStateAtExit = new LinkedHashMap<>();

    private int capacity = MAX_CAPACITY;

    BlockState computeActiveStateOnBlockEntry(BasicBlock block, int maxCapacityPerBlock) {
      if (block.isEntry()) {
        return new BlockState(maxCapacityPerBlock);
      }
      List<BasicBlock> predecessors = block.getPredecessors();
      Iterator<BasicBlock> predecessorIterator = predecessors.iterator();
      BlockState state =
          new BlockState(maxCapacityPerBlock, activeStateAtExit.get(predecessorIterator.next()));
      while (predecessorIterator.hasNext()) {
        BasicBlock predecessor = predecessorIterator.next();
        BlockState predecessorExitState = activeStateAtExit.get(predecessor);
        if (predecessorExitState == null) {
          // Not processed yet.
          return new BlockState(maxCapacityPerBlock);
        }
        state.intersect(predecessorExitState);
      }
      // Allow propagation across exceptional edges, just be careful not to propagate if the
      // throwing instruction is a field instruction.
      for (BasicBlock predecessor : predecessors) {
        if (predecessor.hasCatchSuccessor(block)) {
          Instruction exceptionalExit = predecessor.exceptionalExit();
          if (exceptionalExit != null) {
            if (exceptionalExit.isFieldInstruction()) {
              state.killActiveFieldsForExceptionalExit(exceptionalExit.asFieldInstruction());
            } else if (exceptionalExit.isInitClass()) {
              state.killActiveInitializedClassesForExceptionalExit(exceptionalExit.asInitClass());
            }
          }
        }
      }
      return state;
    }

    private void ensureCapacity(BlockState state) {
      int stateSize = state.size();
      assert stateSize <= state.maxCapacity;
      int numberOfItemsToRemove = stateSize - capacity;
      if (numberOfItemsToRemove <= 0) {
        return;
      }
      Iterator<Entry<BasicBlock, BlockState>> iterator = activeStateAtExit.entrySet().iterator();
      while (iterator.hasNext() && numberOfItemsToRemove > 0) {
        Entry<BasicBlock, BlockState> entry = iterator.next();
        BlockState existingState = entry.getValue();
        int existingStateSize = existingState.size();
        assert existingStateSize > 0;
        if (existingStateSize <= numberOfItemsToRemove) {
          iterator.remove();
          capacity += existingStateSize;
          numberOfItemsToRemove -= existingStateSize;
        } else {
          existingState.reduceSize(numberOfItemsToRemove);
          capacity += numberOfItemsToRemove;
          numberOfItemsToRemove = 0;
        }
      }
      if (numberOfItemsToRemove > 0) {
        state.reduceSize(numberOfItemsToRemove);
      }
      assert capacity == MAX_CAPACITY - size();
    }

    void removeDeadBlockExitStates(
        BasicBlock current, Reference2IntMap<BasicBlock> pendingNormalSuccessorsMap) {
      for (BasicBlock predecessor : current.getPredecessors()) {
        if (predecessor.hasUniqueSuccessor()) {
          removeState(predecessor);
        } else {
          if (predecessor.hasNormalSuccessor(current)) {
            int pendingNormalSuccessors = pendingNormalSuccessorsMap.getInt(predecessor) - 1;
            if (pendingNormalSuccessors == 0) {
              pendingNormalSuccessorsMap.removeInt(predecessor);
              removeState(predecessor);
            } else {
              pendingNormalSuccessorsMap.put(predecessor, pendingNormalSuccessors);
            }
          }
        }
      }
    }

    void recordActiveStateOnBlockExit(BasicBlock block, BlockState state) {
      assert !activeStateAtExit.containsKey(block);
      if (state.isEmpty()) {
        return;
      }
      if (!block.hasUniqueSuccessorWithUniquePredecessor()) {
        state.clearMostRecentFieldWrites();
      }
      ensureCapacity(state);
      activeStateAtExit.put(block, state);
      capacity -= state.size();
      assert capacity >= 0;
    }

    private void removeState(BasicBlock block) {
      BlockState state = activeStateAtExit.remove(block);
      if (state != null) {
        int stateSize = state.size();
        assert stateSize > 0;
        capacity += stateSize;
      }
    }

    private int size() {
      int size = 0;
      for (BlockState state : activeStateAtExit.values()) {
        int stateSize = state.size();
        assert stateSize > 0;
        size += stateSize;
      }
      return size;
    }
  }

  static class BlockState {

    private LinkedHashMap<FieldAndObject, FieldValue> finalInstanceFieldValues;

    private LinkedHashMap<DexField, FieldValue> finalStaticFieldValues;

    private LinkedHashSet<DexType> initializedClasses;

    private LinkedHashMap<FieldAndObject, FieldValue> nonFinalInstanceFieldValues;

    private LinkedHashMap<DexField, FieldValue> nonFinalStaticFieldValues;

    private LinkedHashMap<FieldAndObject, InstancePut> mostRecentInstanceFieldWrites;

    private LinkedHashMap<DexField, StaticPut> mostRecentStaticFieldWrites;

    private final int maxCapacity;

    public BlockState(int maxCapacity) {
      this.maxCapacity = maxCapacity;
    }

    public BlockState(int maxCapacity, BlockState state) {
      this(maxCapacity);
      if (state != null) {
        if (state.finalInstanceFieldValues != null && !state.finalInstanceFieldValues.isEmpty()) {
          finalInstanceFieldValues = new LinkedHashMap<>();
          finalInstanceFieldValues.putAll(state.finalInstanceFieldValues);
        }
        if (state.finalStaticFieldValues != null && !state.finalStaticFieldValues.isEmpty()) {
          finalStaticFieldValues = new LinkedHashMap<>();
          finalStaticFieldValues.putAll(state.finalStaticFieldValues);
        }
        if (state.initializedClasses != null && !state.initializedClasses.isEmpty()) {
          initializedClasses = new LinkedHashSet<>();
          initializedClasses.addAll(state.initializedClasses);
        }
        if (state.nonFinalInstanceFieldValues != null
            && !state.nonFinalInstanceFieldValues.isEmpty()) {
          nonFinalInstanceFieldValues = new LinkedHashMap<>();
          nonFinalInstanceFieldValues.putAll(state.nonFinalInstanceFieldValues);
        }
        if (state.nonFinalStaticFieldValues != null && !state.nonFinalStaticFieldValues.isEmpty()) {
          nonFinalStaticFieldValues = new LinkedHashMap<>();
          nonFinalStaticFieldValues.putAll(state.nonFinalStaticFieldValues);
        }
        if (state.mostRecentInstanceFieldWrites != null
            && !state.mostRecentInstanceFieldWrites.isEmpty()) {
          mostRecentInstanceFieldWrites = new LinkedHashMap<>();
          mostRecentInstanceFieldWrites.putAll(state.mostRecentInstanceFieldWrites);
        }
        if (state.mostRecentStaticFieldWrites != null
            && !state.mostRecentStaticFieldWrites.isEmpty()) {
          mostRecentStaticFieldWrites = new LinkedHashMap<>();
          mostRecentStaticFieldWrites.putAll(state.mostRecentStaticFieldWrites);
        }
      }
    }

    public void clearMostRecentFieldWrites() {
      clearMostRecentInstanceFieldWrites();
      clearMostRecentStaticFieldWrites();
    }

    public void clearMostRecentInstanceFieldWrite(DexField field) {
      if (mostRecentInstanceFieldWrites != null) {
        mostRecentInstanceFieldWrites.keySet().removeIf(key -> key.field == field);
      }
    }

    public void clearMostRecentInstanceFieldWrites() {
      mostRecentInstanceFieldWrites = null;
    }

    public void clearMostRecentStaticFieldWrite(DexField field) {
      if (mostRecentStaticFieldWrites != null) {
        mostRecentStaticFieldWrites.remove(field);
      }
    }

    public void clearMostRecentStaticFieldWrites() {
      mostRecentStaticFieldWrites = null;
    }

    public void clearNonFinalInstanceFields() {
      nonFinalInstanceFieldValues = null;
    }

    public void clearNonFinalStaticFields() {
      nonFinalStaticFieldValues = null;
    }

    public void ensureCapacityForNewElement() {
      int size = size();
      assert size <= maxCapacity;
      if (size == maxCapacity) {
        reduceSize(1);
      }
    }

    public FieldValue getInstanceFieldValue(FieldAndObject field) {
      FieldValue value =
          nonFinalInstanceFieldValues != null ? nonFinalInstanceFieldValues.get(field) : null;
      if (value != null) {
        return value;
      }
      return finalInstanceFieldValues != null ? finalInstanceFieldValues.get(field) : null;
    }

    public FieldValue getStaticFieldValue(DexField field) {
      FieldValue value =
          nonFinalStaticFieldValues != null ? nonFinalStaticFieldValues.get(field) : null;
      if (value != null) {
        return value;
      }
      return finalStaticFieldValues != null ? finalStaticFieldValues.get(field) : null;
    }

    public void intersect(BlockState state) {
      if (finalInstanceFieldValues != null && state.finalInstanceFieldValues != null) {
        intersectFieldValues(finalInstanceFieldValues, state.finalInstanceFieldValues);
      } else {
        finalInstanceFieldValues = null;
      }
      if (finalStaticFieldValues != null && state.finalStaticFieldValues != null) {
        intersectFieldValues(finalStaticFieldValues, state.finalStaticFieldValues);
      } else {
        finalStaticFieldValues = null;
      }
      if (initializedClasses != null && state.initializedClasses != null) {
        intersectInitializedClasses(initializedClasses, state.initializedClasses);
      } else {
        initializedClasses = null;
      }
      if (nonFinalInstanceFieldValues != null && state.nonFinalInstanceFieldValues != null) {
        intersectFieldValues(nonFinalInstanceFieldValues, state.nonFinalInstanceFieldValues);
      } else {
        nonFinalInstanceFieldValues = null;
      }
      if (nonFinalStaticFieldValues != null && state.nonFinalStaticFieldValues != null) {
        intersectFieldValues(nonFinalStaticFieldValues, state.nonFinalStaticFieldValues);
      } else {
        nonFinalStaticFieldValues = null;
      }
      assert mostRecentInstanceFieldWrites == null;
      assert mostRecentStaticFieldWrites == null;
    }

    private static <K> void intersectFieldValues(
        Map<K, FieldValue> fieldValues, Map<K, FieldValue> other) {
      fieldValues.entrySet().removeIf(entry -> other.get(entry.getKey()) != entry.getValue());
    }

    private static void intersectInitializedClasses(
        Set<DexType> initializedClasses, Set<DexType> other) {
      initializedClasses.removeIf(not(other::contains));
    }

    public boolean isClassInitialized(DexType clazz) {
      return initializedClasses != null && initializedClasses.contains(clazz);
    }

    public boolean isEmpty() {
      return isEmpty(finalInstanceFieldValues)
          && isEmpty(finalStaticFieldValues)
          && isEmpty(initializedClasses)
          && isEmpty(nonFinalInstanceFieldValues)
          && isEmpty(nonFinalStaticFieldValues);
    }

    private static boolean isEmpty(Set<?> set) {
      return set == null || set.isEmpty();
    }

    private static boolean isEmpty(Map<?, ?> map) {
      return map == null || map.isEmpty();
    }

    // If a field get instruction throws an exception it did not have an effect on the value of the
    // field. Therefore, when propagating across exceptional edges for a field get instruction we
    // have to exclude that field from the set of known field values.
    public void killActiveFieldsForExceptionalExit(FieldInstruction instruction) {
      DexField field = instruction.getField();
      if (instruction.isInstanceGet()) {
        Value object = instruction.asInstanceGet().object().getAliasedValue();
        FieldAndObject fieldAndObject = new FieldAndObject(field, object);
        removeInstanceField(fieldAndObject);
      } else if (instruction.isStaticGet()) {
        removeStaticField(field);
      }
    }

    private void killActiveInitializedClassesForExceptionalExit(InitClass instruction) {
      if (initializedClasses != null) {
        initializedClasses.remove(instruction.getClassValue());
      }
    }

    public boolean markClassAsInitialized(DexType clazz) {
      ensureCapacityForNewElement();
      if (initializedClasses == null) {
        initializedClasses = new LinkedHashSet<>();
      }
      return initializedClasses.add(clazz);
    }

    public void reduceSize(int numberOfItemsToRemove) {
      assert numberOfItemsToRemove > 0;
      assert numberOfItemsToRemove < size();
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, initializedClasses);
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, nonFinalInstanceFieldValues);
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, nonFinalStaticFieldValues);
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, finalInstanceFieldValues);
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, finalStaticFieldValues);
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, mostRecentInstanceFieldWrites);
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, mostRecentStaticFieldWrites);
      assert numberOfItemsToRemove == 0;
    }

    private static int reduceSize(int numberOfItemsToRemove, Set<?> set) {
      if (set == null || numberOfItemsToRemove == 0) {
        return numberOfItemsToRemove;
      }
      Iterator<?> iterator = set.iterator();
      while (iterator.hasNext() && numberOfItemsToRemove > 0) {
        iterator.next();
        iterator.remove();
        numberOfItemsToRemove--;
      }
      return numberOfItemsToRemove;
    }

    private static int reduceSize(int numberOfItemsToRemove, Map<?, ?> map) {
      return reduceSize(numberOfItemsToRemove, map != null ? map.keySet() : null);
    }

    public void removeInstanceField(FieldAndObject field) {
      removeFinalInstanceField(field);
      removeNonFinalInstanceField(field);
      removeMostRecentInstanceFieldWrite(field);
    }

    public void removeFinalInstanceField(FieldAndObject field) {
      if (finalInstanceFieldValues != null) {
        finalInstanceFieldValues.remove(field);
      }
    }

    public void removeNonFinalInstanceField(FieldAndObject field) {
      if (nonFinalInstanceFieldValues != null) {
        nonFinalInstanceFieldValues.remove(field);
      }
    }

    public void removeNonFinalInstanceFields(DexField field) {
      if (nonFinalInstanceFieldValues != null) {
        nonFinalInstanceFieldValues.keySet().removeIf(key -> key.field == field);
      }
    }

    public void removeStaticField(DexField field) {
      removeFinalStaticField(field);
      removeNonFinalStaticField(field);
      removeMostRecentStaticFieldWrite(field);
    }

    public void removeFinalStaticField(DexField field) {
      if (finalStaticFieldValues != null) {
        finalStaticFieldValues.remove(field);
      }
    }

    public void removeNonFinalStaticField(DexField field) {
      if (nonFinalStaticFieldValues != null) {
        nonFinalStaticFieldValues.remove(field);
      }
    }

    public void removeMostRecentInstanceFieldWrite(FieldAndObject field) {
      if (mostRecentInstanceFieldWrites != null) {
        mostRecentInstanceFieldWrites.remove(field);
      }
    }

    public void removeMostRecentStaticFieldWrite(DexField field) {
      if (mostRecentStaticFieldWrites != null) {
        mostRecentStaticFieldWrites.remove(field);
      }
    }

    public void putFinalInstanceField(FieldAndObject field, FieldValue value) {
      ensureCapacityForNewElement();
      if (finalInstanceFieldValues == null) {
        finalInstanceFieldValues = new LinkedHashMap<>();
      }
      finalInstanceFieldValues.put(field, value);
    }

    public void putFinalStaticField(DexField field, FieldValue value) {
      ensureCapacityForNewElement();
      if (finalStaticFieldValues == null) {
        finalStaticFieldValues = new LinkedHashMap<>();
      }
      finalStaticFieldValues.put(field, value);
    }

    public InstancePut putMostRecentInstanceFieldWrite(
        FieldAndObject field, InstancePut instancePut) {
      ensureCapacityForNewElement();
      if (mostRecentInstanceFieldWrites == null) {
        mostRecentInstanceFieldWrites = new LinkedHashMap<>();
      }
      return mostRecentInstanceFieldWrites.put(field, instancePut);
    }

    public StaticPut putMostRecentStaticFieldWrite(DexField field, StaticPut staticPut) {
      ensureCapacityForNewElement();
      if (mostRecentStaticFieldWrites == null) {
        mostRecentStaticFieldWrites = new LinkedHashMap<>();
      }
      return mostRecentStaticFieldWrites.put(field, staticPut);
    }

    public void putNonFinalInstanceField(FieldAndObject field, FieldValue value) {
      ensureCapacityForNewElement();
      assert finalInstanceFieldValues == null || !finalInstanceFieldValues.containsKey(field);
      if (nonFinalInstanceFieldValues == null) {
        nonFinalInstanceFieldValues = new LinkedHashMap<>();
      }
      nonFinalInstanceFieldValues.put(field, value);
    }

    public void putNonFinalStaticField(DexField field, FieldValue value) {
      ensureCapacityForNewElement();
      assert nonFinalStaticFieldValues == null || !nonFinalStaticFieldValues.containsKey(field);
      if (nonFinalStaticFieldValues == null) {
        nonFinalStaticFieldValues = new LinkedHashMap<>();
      }
      nonFinalStaticFieldValues.put(field, value);
    }

    public int size() {
      return size(finalInstanceFieldValues)
          + size(finalStaticFieldValues)
          + size(initializedClasses)
          + size(nonFinalInstanceFieldValues)
          + size(nonFinalStaticFieldValues)
          + size(mostRecentInstanceFieldWrites)
          + size(mostRecentStaticFieldWrites);
    }

    private static int size(Set<?> set) {
      return set != null ? set.size() : 0;
    }

    private static int size(Map<?, ?> map) {
      return map != null ? map.size() : 0;
    }
  }
}
