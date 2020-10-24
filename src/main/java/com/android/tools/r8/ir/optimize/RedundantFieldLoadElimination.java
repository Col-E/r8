// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

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
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.BasicBlock;
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
public class RedundantFieldLoadElimination {

  private static final int MAX_CAPACITY = 10000;
  private static final int MAX_CAPACITY_PER_BLOCK = 50;

  private final AppView<?> appView;
  private final ProgramMethod method;
  private final IRCode code;

  // Values that may require type propagation.
  private final Set<Value> affectedValues = Sets.newIdentityHashSet();

  // Maps keeping track of fields that have an already loaded value at basic block entry.
  private final BlockStates activeStates = new BlockStates();

  // Maps keeping track of fields with already loaded values for the current block during
  // elimination.
  private BlockState activeState;

  public RedundantFieldLoadElimination(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.method = code.context();
    this.code = code;
  }

  public static boolean shouldRun(AppView<?> appView, IRCode code) {
    return appView.options().enableRedundantFieldLoadElimination
        && (code.metadata().mayHaveFieldGet() || code.metadata().mayHaveInitClass());
  }

  private interface FieldValue {

    void eliminateRedundantRead(InstructionListIterator it, FieldInstruction redundant);
  }

  private class ExistingValue implements FieldValue {

    private final Value value;

    private ExistingValue(Value value) {
      this.value = value;
    }

    @Override
    public void eliminateRedundantRead(InstructionListIterator it, FieldInstruction redundant) {
      affectedValues.addAll(redundant.value().affectedValues());
      redundant.value().replaceUsers(value);
      it.removeOrReplaceByDebugLocalRead();
      value.uniquePhiUsers().forEach(Phi::removeTrivialPhi);
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

    for (BasicBlock head : code.topologicallySortedBlocks()) {
      if (head.hasUniquePredecessor() && head.getUniquePredecessor().hasUniqueNormalSuccessor()) {
        // Already visited.
        continue;
      }
      activeState = activeStates.computeActiveStateOnBlockEntry(head);
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
              InstanceGet instanceGet = instruction.asInstanceGet();
              if (instanceGet.outValue().hasLocalInfo()) {
                continue;
              }
              Value object = instanceGet.object().getAliasedValue();
              FieldAndObject fieldAndObject = new FieldAndObject(reference, object);
              FieldValue replacement = activeState.getInstanceFieldValue(fieldAndObject);
              if (replacement != null) {
                replacement.eliminateRedundantRead(it, instanceGet);
              } else {
                activeState.putNonFinalInstanceField(
                    fieldAndObject, new ExistingValue(instanceGet.value()));
              }
            } else if (instruction.isInstancePut()) {
              InstancePut instancePut = instruction.asInstancePut();
              // An instance-put instruction can potentially write the given field on all objects
              // because of aliases.
              killNonFinalActiveFields(instancePut);
              // ... but at least we know the field value for this particular object.
              Value object = instancePut.object().getAliasedValue();
              FieldAndObject fieldAndObject = new FieldAndObject(reference, object);
              ExistingValue value = new ExistingValue(instancePut.value());
              if (isFinal(field)) {
                assert !field.getDefinition().isFinal()
                    || method.getDefinition().isInstanceInitializer()
                    || verifyWasInstanceInitializer();
                activeState.putFinalInstanceField(fieldAndObject, value);
              } else {
                activeState.putNonFinalInstanceField(fieldAndObject, value);
              }
            } else if (instruction.isStaticGet()) {
              StaticGet staticGet = instruction.asStaticGet();
              if (staticGet.outValue().hasLocalInfo()) {
                continue;
              }
              FieldValue replacement = activeState.getStaticFieldValue(reference);
              if (replacement != null) {
                replacement.eliminateRedundantRead(it, staticGet);
              } else {
                // A field get on a different class can cause <clinit> to run and change static
                // field values.
                killNonFinalActiveFields(staticGet);
                FieldValue value = new ExistingValue(staticGet.value());
                if (isFinal(field)) {
                  activeState.putFinalStaticField(reference, value);
                } else {
                  activeState.putNonFinalStaticField(reference, value);
                }
              }
            } else if (instruction.isStaticPut()) {
              StaticPut staticPut = instruction.asStaticPut();
              // A field put on a different class can cause <clinit> to run and change static
              // field values.
              killNonFinalActiveFields(staticPut);
              ExistingValue value = new ExistingValue(staticPut.value());
              if (isFinal(field)) {
                assert !field.getDefinition().isFinal()
                    || method.getDefinition().isClassInitializer();
                activeState.putFinalStaticField(reference, value);
              } else {
                activeState.putNonFinalStaticField(reference, value);
              }
            }
          } else if (instruction.isInitClass()) {
            InitClass initClass = instruction.asInitClass();
            assert !initClass.outValue().hasAnyUsers();
            DexType clazz = initClass.getClassValue();
            if (activeState.isClassInitialized(clazz)) {
              it.removeOrReplaceByDebugLocalRead();
            }
            activeState.markClassAsInitialized(clazz);
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
            if (newInstance.clazz.classInitializationMayHaveSideEffects(
                appView,
                // Types that are a super type of `context` are guaranteed to be initialized
                // already.
                type -> appView.isSubtype(method.getHolderType(), type).isTrue(),
                Sets.newIdentityHashSet())) {
              killAllNonFinalActiveFields();
            }
          } else {
            // If the current instruction could trigger a method invocation, it could also cause
            // field values to change. In that case, it must be handled above.
            assert !instruction.instructionMayTriggerMethodInvocation(appView, method);

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
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
  }

  private boolean verifyWasInstanceInitializer() {
    VerticallyMergedClasses verticallyMergedClasses = appView.verticallyMergedClasses();
    assert verticallyMergedClasses != null;
    assert verticallyMergedClasses.isTarget(method.getHolderType());
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
        singleTarget.getDefinition().getOptimizationInfo().getInstanceInitializerInfo();
    if (instanceInitializerInfo.mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
      killAllNonFinalActiveFields();
    }

    InstanceFieldInitializationInfoCollection fieldInitializationInfos =
        instanceInitializerInfo.fieldInitializationInfos();
    fieldInitializationInfos.forEachWithDeterministicOrder(
        appView,
        (field, info) -> {
          if (!appView.appInfo().withLiveness().mayPropagateValueFor(field.field)) {
            return;
          }
          if (info.isArgumentInitializationInfo()) {
            Value value =
                invoke.getArgument(info.asArgumentInitializationInfo().getArgumentIndex());
            Value object = invoke.getReceiver().getAliasedValue();
            FieldAndObject fieldAndObject = new FieldAndObject(field.field, object);
            if (field.isFinal()) {
              activeState.putFinalInstanceField(fieldAndObject, new ExistingValue(value));
            } else {
              activeState.putNonFinalInstanceField(fieldAndObject, new ExistingValue(value));
            }
          } else if (info.isSingleValue()) {
            SingleValue value = info.asSingleValue();
            if (value.isMaterializableInContext(appView.withLiveness(), method)) {
              Value object = invoke.getReceiver().getAliasedValue();
              FieldAndObject fieldAndObject = new FieldAndObject(field.field, object);
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

  private void killAllNonFinalActiveFields() {
    activeState.clearNonFinalInstanceFields();
    activeState.clearNonFinalStaticFields();
  }

  private void killNonFinalActiveFields(FieldInstruction instruction) {
    DexField field = instruction.getField();
    if (instruction.isInstancePut()) {
      // Remove all the field/object pairs that refer to this field to make sure
      // that we are conservative.
      activeState.removeNonFinalInstanceFields(field);
    } else if (instruction.isStaticPut()) {
      if (field.holder != code.method().holder()) {
        // Accessing a static field on a different object could cause <clinit> to run which
        // could modify any static field on any other object.
        activeState.clearNonFinalStaticFields();
      } else {
        activeState.removeNonFinalStaticField(field);
      }
    } else if (instruction.isStaticGet()) {
      if (field.holder != code.method().holder()) {
        // Accessing a static field on a different object could cause <clinit> to run which
        // could modify any static field on any other object.
        activeState.clearNonFinalStaticFields();
      }
    } else if (instruction.isInstanceGet()) {
      throw new Unreachable();
    }
  }

  static class BlockStates {

    // Maps keeping track of fields that have an already loaded value at basic block entry.
    private final LinkedHashMap<BasicBlock, BlockState> activeStateAtExit = new LinkedHashMap<>();

    private int capacity = MAX_CAPACITY;

    BlockState computeActiveStateOnBlockEntry(BasicBlock block) {
      if (block.isEntry()) {
        return new BlockState();
      }
      List<BasicBlock> predecessors = block.getPredecessors();
      Iterator<BasicBlock> predecessorIterator = predecessors.iterator();
      BlockState state = new BlockState(activeStateAtExit.get(predecessorIterator.next()));
      while (predecessorIterator.hasNext()) {
        BasicBlock predecessor = predecessorIterator.next();
        BlockState predecessorExitState = activeStateAtExit.get(predecessor);
        if (predecessorExitState == null) {
          // Not processed yet.
          return new BlockState();
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
      assert stateSize <= MAX_CAPACITY_PER_BLOCK;
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

    public BlockState() {}

    public BlockState(BlockState state) {
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
      }
    }

    public void clearNonFinalInstanceFields() {
      nonFinalInstanceFieldValues = null;
    }

    public void clearNonFinalStaticFields() {
      nonFinalStaticFieldValues = null;
    }

    public void ensureCapacityForNewElement() {
      int size = size();
      assert size <= MAX_CAPACITY_PER_BLOCK;
      if (size == MAX_CAPACITY_PER_BLOCK) {
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

    public void markClassAsInitialized(DexType clazz) {
      ensureCapacityForNewElement();
      if (initializedClasses == null) {
        initializedClasses = new LinkedHashSet<>();
      }
      initializedClasses.add(clazz);
    }

    public void reduceSize(int numberOfItemsToRemove) {
      assert numberOfItemsToRemove > 0;
      assert numberOfItemsToRemove < size();
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, initializedClasses);
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, nonFinalInstanceFieldValues);
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, nonFinalStaticFieldValues);
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, finalInstanceFieldValues);
      numberOfItemsToRemove = reduceSize(numberOfItemsToRemove, finalStaticFieldValues);
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
          + size(nonFinalStaticFieldValues);
    }

    private static int size(Set<?> set) {
      return set != null ? set.size() : 0;
    }

    private static int size(Map<?, ?> map) {
      return map != null ? map.size() : 0;
    }
  }
}
