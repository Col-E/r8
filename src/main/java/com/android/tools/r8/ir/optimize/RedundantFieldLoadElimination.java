// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DominatorTree;
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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Eliminate redundant field loads.
 *
 * <p>Simple algorithm that goes through all blocks in one pass in dominator order and propagates
 * active field sets across control-flow edges where the target has only one predecessor.
 */
// TODO(ager): Evaluate speed/size for computing active field sets in a fixed-point computation.
public class RedundantFieldLoadElimination {

  private final AppView<?> appView;
  private final DexEncodedMethod method;
  private final IRCode code;
  private final DominatorTree dominatorTree;

  // Values that may require type propagation.
  private final Set<Value> affectedValues = Sets.newIdentityHashSet();

  // Maps keeping track of fields that have an already loaded value at basic block entry.
  private final Map<BasicBlock, State> activeStateAtExit = new IdentityHashMap<>();

  // Maps keeping track of fields with already loaded values for the current block during
  // elimination.
  private State activeState;

  public RedundantFieldLoadElimination(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.method = code.method;
    this.code = code;
    dominatorTree = new DominatorTree(code);
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
  }

  private class MaterializableValue implements FieldValue {

    private final SingleValue value;

    private MaterializableValue(SingleValue value) {
      assert value.isMaterializableInContext(appView, method.holder());
      this.value = value;
    }

    @Override
    public void eliminateRedundantRead(InstructionListIterator it, FieldInstruction redundant) {
      affectedValues.addAll(redundant.value().affectedValues());
      it.replaceCurrentInstruction(
          value.createMaterializingInstruction(appView.withSubtyping(), code, redundant));
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

  private DexEncodedField resolveField(DexField field) {
    if (appView.enableWholeProgramOptimizations()) {
      return appView.appInfo().resolveField(field);
    }
    if (field.holder == method.holder()) {
      return appView.definitionFor(field);
    }
    return null;
  }

  public void run() {
    DexType context = method.holder();
    for (BasicBlock block : dominatorTree.getSortedBlocks()) {
      computeActiveStateOnBlockEntry(block);
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (instruction.isFieldInstruction()) {
          DexField field = instruction.asFieldInstruction().getField();
          DexEncodedField definition = resolveField(field);
          if (definition == null || definition.isVolatile()) {
            killAllNonFinalActiveFields();
            continue;
          }

          if (instruction.isInstanceGet()) {
            InstanceGet instanceGet = instruction.asInstanceGet();
            if (instanceGet.outValue().hasLocalInfo()) {
              continue;
            }
            Value object = instanceGet.object().getAliasedValue();
            FieldAndObject fieldAndObject = new FieldAndObject(field, object);
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
            FieldAndObject fieldAndObject = new FieldAndObject(field, object);
            ExistingValue value = new ExistingValue(instancePut.value());
            if (definition.isFinal()) {
              assert method.isInstanceInitializer() || verifyWasInstanceInitializer();
              activeState.putFinalInstanceField(fieldAndObject, value);
            } else {
              activeState.putNonFinalInstanceField(fieldAndObject, value);
            }
          } else if (instruction.isStaticGet()) {
            StaticGet staticGet = instruction.asStaticGet();
            if (staticGet.outValue().hasLocalInfo()) {
              continue;
            }
            FieldValue replacement = activeState.getStaticFieldValue(field);
            if (replacement != null) {
              replacement.eliminateRedundantRead(it, staticGet);
            } else {
              // A field get on a different class can cause <clinit> to run and change static
              // field values.
              killNonFinalActiveFields(staticGet);
              activeState.putNonFinalStaticField(field, new ExistingValue(staticGet.value()));
            }
          } else if (instruction.isStaticPut()) {
            StaticPut staticPut = instruction.asStaticPut();
            // A field put on a different class can cause <clinit> to run and change static
            // field values.
            killNonFinalActiveFields(staticPut);
            ExistingValue value = new ExistingValue(staticPut.value());
            if (definition.isFinal()) {
              assert method.isClassInitializer();
              activeState.putFinalStaticField(field, value);
            } else {
              activeState.putNonFinalStaticField(field, value);
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
              // Types that are a super type of `context` are guaranteed to be initialized already.
              type -> appView.isSubtype(context, type).isTrue(),
              Sets.newIdentityHashSet())) {
            killAllNonFinalActiveFields();
          }
        } else {
          // If the current instruction could trigger a method invocation, it could also cause field
          // values to change. In that case, it must be handled above.
          assert !instruction.instructionMayTriggerMethodInvocation(appView, context);

          // If this assertion fails for a new instruction we need to determine if that instruction
          // has side-effects that can change the value of fields. If so, it must be handled above.
          // If not, it can be safely added to the assert.
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
      recordActiveStateOnBlockExit(block);
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
  }

  private boolean verifyWasInstanceInitializer() {
    VerticallyMergedClasses verticallyMergedClasses = appView.verticallyMergedClasses();
    assert verticallyMergedClasses != null;
    assert verticallyMergedClasses.isTarget(method.holder());
    assert appView
        .dexItemFactory()
        .isConstructor(appView.graphLense().getOriginalMethodSignature(method.method));
    assert method.getOptimizationInfo().forceInline();
    return true;
  }

  private void handleInvokeDirect(InvokeDirect invoke) {
    if (!appView.enableWholeProgramOptimizations()) {
      killAllNonFinalActiveFields();
      return;
    }

    DexEncodedMethod singleTarget = invoke.lookupSingleTarget(appView, method.holder());
    if (singleTarget == null || !singleTarget.isInstanceInitializer()) {
      killAllNonFinalActiveFields();
      return;
    }

    InstanceInitializerInfo instanceInitializerInfo =
        singleTarget.getOptimizationInfo().getInstanceInitializerInfo();
    if (instanceInitializerInfo.mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
      killAllNonFinalActiveFields();
    }

    InstanceFieldInitializationInfoCollection fieldInitializationInfos =
        instanceInitializerInfo.fieldInitializationInfos();
    fieldInitializationInfos.forEach(
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
            activeState.putNonFinalInstanceField(fieldAndObject, new ExistingValue(value));
          } else if (info.isSingleValue()) {
            SingleValue value = info.asSingleValue();
            if (value.isMaterializableInContext(appView, method.holder())) {
              Value object = invoke.getReceiver().getAliasedValue();
              FieldAndObject fieldAndObject = new FieldAndObject(field.field, object);
              activeState.putNonFinalInstanceField(fieldAndObject, new MaterializableValue(value));
            }
          } else {
            assert info.isTypeInitializationInfo();
          }
        });
  }

  private void computeActiveStateOnBlockEntry(BasicBlock block) {
    if (block.isEntry()) {
      activeState = new State();
      return;
    }
    Deque<State> predecessorExitStates = new ArrayDeque<>(block.getPredecessors().size());
    for (BasicBlock predecessor : block.getPredecessors()) {
      State predecessorExitState = activeStateAtExit.get(predecessor);
      if (predecessorExitState == null) {
        // Not processed yet.
        activeState = new State();
        return;
      }
      // Allow propagation across exceptional edges, just be careful not to propagate if the
      // throwing instruction is a field instruction.
      if (predecessor.hasCatchSuccessor(block)) {
        Instruction exceptionalExit = predecessor.exceptionalExit();
        if (exceptionalExit != null) {
          predecessorExitState = new State(predecessorExitState);
          if (exceptionalExit.isFieldInstruction()) {
            predecessorExitState.killActiveFieldsForExceptionalExit(
                exceptionalExit.asFieldInstruction());
          } else if (exceptionalExit.isInitClass()) {
            predecessorExitState.killActiveInitializedClassesForExceptionalExit(
                exceptionalExit.asInitClass());
          }
        }
      }
      predecessorExitStates.addLast(predecessorExitState);
    }
    State state = new State(predecessorExitStates.removeFirst());
    predecessorExitStates.forEach(state::intersect);
    activeState = state;
  }

  private void recordActiveStateOnBlockExit(BasicBlock block) {
    assert !activeStateAtExit.containsKey(block);
    activeStateAtExit.put(block, activeState);
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
      if (field.holder != code.method.holder()) {
        // Accessing a static field on a different object could cause <clinit> to run which
        // could modify any static field on any other object.
        activeState.clearNonFinalStaticFields();
      } else {
        activeState.removeNonFinalStaticField(field);
      }
    } else if (instruction.isStaticGet()) {
      if (field.holder != code.method.holder()) {
        // Accessing a static field on a different object could cause <clinit> to run which
        // could modify any static field on any other object.
        activeState.clearNonFinalStaticFields();
      }
    } else if (instruction.isInstanceGet()) {
      throw new Unreachable();
    }
  }

  static class State {

    private final Map<FieldAndObject, FieldValue> finalInstanceFieldValues = new HashMap<>();

    private final Map<DexField, FieldValue> finalStaticFieldValues = new IdentityHashMap<>();

    private final Set<DexType> initializedClasses = Sets.newIdentityHashSet();

    private final Map<FieldAndObject, FieldValue> nonFinalInstanceFieldValues = new HashMap<>();

    private final Map<DexField, FieldValue> nonFinalStaticFieldValues = new IdentityHashMap<>();

    public State() {}

    public State(State state) {
      finalInstanceFieldValues.putAll(state.finalInstanceFieldValues);
      finalStaticFieldValues.putAll(state.finalStaticFieldValues);
      initializedClasses.addAll(state.initializedClasses);
      nonFinalInstanceFieldValues.putAll(state.nonFinalInstanceFieldValues);
      nonFinalStaticFieldValues.putAll(state.nonFinalStaticFieldValues);
    }

    public void clearNonFinalInstanceFields() {
      nonFinalInstanceFieldValues.clear();
    }

    public void clearNonFinalStaticFields() {
      nonFinalStaticFieldValues.clear();
    }

    public FieldValue getInstanceFieldValue(FieldAndObject field) {
      FieldValue value = nonFinalInstanceFieldValues.get(field);
      return value != null ? value : finalInstanceFieldValues.get(field);
    }

    public FieldValue getStaticFieldValue(DexField field) {
      FieldValue value = nonFinalStaticFieldValues.get(field);
      return value != null ? value : finalStaticFieldValues.get(field);
    }

    public void intersect(State state) {
      intersectFieldValues(finalInstanceFieldValues, state.finalInstanceFieldValues);
      intersectFieldValues(finalStaticFieldValues, state.finalStaticFieldValues);
      intersectInitializedClasses(initializedClasses, state.initializedClasses);
      intersectFieldValues(nonFinalInstanceFieldValues, state.nonFinalInstanceFieldValues);
      intersectFieldValues(nonFinalStaticFieldValues, state.nonFinalStaticFieldValues);
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
      return initializedClasses.contains(clazz);
    }

    // If a field get instruction throws an exception it did not have an effect on the value of the
    // field. Therefore, when propagating across exceptional edges for a field get instruction we
    // have to exclude that field from the set of known field values.
    public void killActiveFieldsForExceptionalExit(FieldInstruction instruction) {
      DexField field = instruction.getField();
      if (instruction.isInstanceGet()) {
        Value object = instruction.asInstanceGet().object().getAliasedValue();
        FieldAndObject fieldAndObject = new FieldAndObject(field, object);
        removeNonFinalInstanceField(fieldAndObject);
      } else if (instruction.isStaticGet()) {
        removeNonFinalStaticField(field);
      }
    }

    private void killActiveInitializedClassesForExceptionalExit(InitClass instruction) {
      initializedClasses.remove(instruction.getClassValue());
    }

    public void markClassAsInitialized(DexType clazz) {
      initializedClasses.add(clazz);
    }

    public void removeInstanceField(FieldAndObject field) {
      removeFinalInstanceField(field);
      removeNonFinalInstanceField(field);
    }

    public void removeFinalInstanceField(FieldAndObject field) {
      finalInstanceFieldValues.remove(field);
    }

    public void removeNonFinalInstanceField(FieldAndObject field) {
      nonFinalInstanceFieldValues.remove(field);
    }

    public void removeNonFinalInstanceFields(DexField field) {
      nonFinalInstanceFieldValues.keySet().removeIf(key -> key.field == field);
    }

    public void removeStaticField(DexField field) {
      removeFinalStaticField(field);
      removeNonFinalStaticField(field);
    }

    public void removeFinalStaticField(DexField field) {
      finalStaticFieldValues.remove(field);
    }

    public void removeNonFinalStaticField(DexField field) {
      nonFinalStaticFieldValues.remove(field);
    }

    public void putFinalInstanceField(FieldAndObject field, FieldValue value) {
      finalInstanceFieldValues.put(field, value);
    }

    public void putFinalStaticField(DexField field, FieldValue value) {
      finalStaticFieldValues.put(field, value);
    }

    public void putNonFinalInstanceField(FieldAndObject field, FieldValue value) {
      assert !finalInstanceFieldValues.containsKey(field);
      nonFinalInstanceFieldValues.put(field, value);
    }

    public void putNonFinalStaticField(DexField field, FieldValue value) {
      assert !nonFinalStaticFieldValues.containsKey(field);
      nonFinalStaticFieldValues.put(field, value);
    }
  }
}
