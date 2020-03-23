// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
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
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
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
  private final Map<BasicBlock, Set<DexType>> activeInitializedClassesAtEntry =
      new IdentityHashMap<>();
  private final Map<BasicBlock, Map<FieldAndObject, FieldValue>> activeInstanceFieldsAtEntry =
      new IdentityHashMap<>();
  private final Map<BasicBlock, Map<DexField, FieldValue>> activeStaticFieldsAtEntry =
      new IdentityHashMap<>();

  // Maps keeping track of fields with already loaded values for the current block during
  // elimination.
  private Set<DexType> activeInitializedClasses;
  private Map<FieldAndObject, FieldValue> activeInstanceFieldValues;
  private Map<DexField, FieldValue> activeStaticFieldValues;

  public RedundantFieldLoadElimination(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.method = code.method;
    this.code = code;
    dominatorTree = new DominatorTree(code);
  }

  public static boolean shouldRun(AppView<?> appView, IRCode code) {
    return appView.options().enableRedundantFieldLoadElimination
        && code.metadata().mayHaveFieldGet();
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

  private boolean couldBeVolatile(DexField field) {
    DexEncodedField definition;
    if (appView.enableWholeProgramOptimizations()) {
      definition = appView.appInfo().resolveField(field);
    } else {
      if (field.holder != method.holder()) {
        return true;
      }
      definition = appView.definitionFor(field);
    }
    return definition == null || definition.accessFlags.isVolatile();
  }

  public void run() {
    DexType context = method.holder();
    for (BasicBlock block : dominatorTree.getSortedBlocks()) {
      activeInitializedClasses =
          activeInitializedClassesAtEntry.containsKey(block)
              ? activeInitializedClassesAtEntry.get(block)
              : Sets.newIdentityHashSet();
      activeInstanceFieldValues =
          activeInstanceFieldsAtEntry.containsKey(block)
              ? activeInstanceFieldsAtEntry.get(block)
              : new HashMap<>();
      activeStaticFieldValues =
          activeStaticFieldsAtEntry.containsKey(block)
              ? activeStaticFieldsAtEntry.get(block)
              : new IdentityHashMap<>();
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (instruction.isFieldInstruction()) {
          DexField field = instruction.asFieldInstruction().getField();
          if (couldBeVolatile(field)) {
            killAllActiveFields();
            continue;
          }

          if (instruction.isInstanceGet()) {
            InstanceGet instanceGet = instruction.asInstanceGet();
            if (instanceGet.outValue().hasLocalInfo()) {
              continue;
            }
            Value object = instanceGet.object().getAliasedValue();
            FieldAndObject fieldAndObject = new FieldAndObject(field, object);
            if (activeInstanceFieldValues.containsKey(fieldAndObject)) {
              FieldValue replacement = activeInstanceFieldValues.get(fieldAndObject);
              replacement.eliminateRedundantRead(it, instanceGet);
            } else {
              activeInstanceFieldValues.put(fieldAndObject, new ExistingValue(instanceGet.value()));
            }
          } else if (instruction.isInstancePut()) {
            InstancePut instancePut = instruction.asInstancePut();
            // An instance-put instruction can potentially write the given field on all objects
            // because of aliases.
            killActiveFields(instancePut);
            // ... but at least we know the field value for this particular object.
            Value object = instancePut.object().getAliasedValue();
            FieldAndObject fieldAndObject = new FieldAndObject(field, object);
            activeInstanceFieldValues.put(fieldAndObject, new ExistingValue(instancePut.value()));
          } else if (instruction.isStaticGet()) {
            StaticGet staticGet = instruction.asStaticGet();
            if (staticGet.outValue().hasLocalInfo()) {
              continue;
            }
            if (activeStaticFieldValues.containsKey(field)) {
              FieldValue replacement = activeStaticFieldValues.get(field);
              replacement.eliminateRedundantRead(it, staticGet);
            } else {
              // A field get on a different class can cause <clinit> to run and change static
              // field values.
              killActiveFields(staticGet);
              activeStaticFieldValues.put(field, new ExistingValue(staticGet.value()));
            }
          } else if (instruction.isStaticPut()) {
            StaticPut staticPut = instruction.asStaticPut();
            // A field put on a different class can cause <clinit> to run and change static
            // field values.
            killActiveFields(staticPut);
            activeStaticFieldValues.put(field, new ExistingValue(staticPut.value()));
          }
        } else if (instruction.isInitClass()) {
          InitClass initClass = instruction.asInitClass();
          assert !initClass.outValue().hasAnyUsers();
          if (activeInitializedClasses.contains(initClass.getClassValue())) {
            it.removeOrReplaceByDebugLocalRead();
          }
        } else if (instruction.isMonitor()) {
          if (instruction.asMonitor().isEnter()) {
            killAllActiveFields();
          }
        } else if (instruction.isInvokeDirect()) {
          handleInvokeDirect(instruction.asInvokeDirect());
        } else if (instruction.isInvokeMethod() || instruction.isInvokeCustom()) {
          killAllActiveFields();
        } else if (instruction.isNewInstance()) {
          NewInstance newInstance = instruction.asNewInstance();
          if (newInstance.clazz.classInitializationMayHaveSideEffects(
              appView,
              // Types that are a super type of `context` are guaranteed to be initialized already.
              type -> appView.isSubtype(context, type).isTrue(),
              Sets.newIdentityHashSet())) {
            killAllActiveFields();
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
      propagateActiveStateFrom(block);
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
  }

  private void handleInvokeDirect(InvokeDirect invoke) {
    if (!appView.enableWholeProgramOptimizations()) {
      killAllActiveFields();
      return;
    }

    DexEncodedMethod singleTarget = invoke.lookupSingleTarget(appView, method.holder());
    if (singleTarget == null || !singleTarget.isInstanceInitializer()) {
      killAllActiveFields();
      return;
    }

    InstanceInitializerInfo instanceInitializerInfo =
        singleTarget.getOptimizationInfo().getInstanceInitializerInfo();
    if (instanceInitializerInfo.mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
      killAllActiveFields();
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
            activeInstanceFieldValues.put(fieldAndObject, new ExistingValue(value));
          } else if (info.isSingleValue()) {
            SingleValue value = info.asSingleValue();
            if (value.isMaterializableInContext(appView, method.holder())) {
              Value object = invoke.getReceiver().getAliasedValue();
              FieldAndObject fieldAndObject = new FieldAndObject(field.field, object);
              activeInstanceFieldValues.put(fieldAndObject, new MaterializableValue(value));
            }
          } else {
            assert info.isTypeInitializationInfo();
          }
        });
  }

  private void propagateActiveStateFrom(BasicBlock block) {
    for (BasicBlock successor : block.getSuccessors()) {
      // Allow propagation across exceptional edges, just be careful not to propagate if the
      // throwing instruction is a field instruction.
      if (successor.getPredecessors().size() == 1) {
        if (block.hasCatchSuccessor(successor)) {
          Instruction exceptionalExit = block.exceptionalExit();
          if (exceptionalExit != null) {
            if (exceptionalExit.isFieldInstruction()) {
              killActiveFieldsForExceptionalExit(exceptionalExit.asFieldInstruction());
            } else if (exceptionalExit.isInitClass()) {
              killActiveInitializedClassesForExceptionalExit(exceptionalExit.asInitClass());
            }
          }
        }
        assert !activeInitializedClassesAtEntry.containsKey(successor);
        activeInitializedClassesAtEntry.put(
            successor, SetUtils.newIdentityHashSet(activeInitializedClasses));
        assert !activeInstanceFieldsAtEntry.containsKey(successor);
        activeInstanceFieldsAtEntry.put(successor, new HashMap<>(activeInstanceFieldValues));
        assert !activeStaticFieldsAtEntry.containsKey(successor);
        activeStaticFieldsAtEntry.put(successor, new IdentityHashMap<>(activeStaticFieldValues));
      }
    }
  }

  private void killAllActiveFields() {
    activeInstanceFieldValues.clear();
    activeStaticFieldValues.clear();
  }

  private void killActiveFields(FieldInstruction instruction) {
    DexField field = instruction.getField();
    if (instruction.isInstancePut()) {
      // Remove all the field/object pairs that refer to this field to make sure
      // that we are conservative.
      List<FieldAndObject> keysToRemove = new ArrayList<>();
      for (FieldAndObject key : activeInstanceFieldValues.keySet()) {
        if (key.field == field) {
          keysToRemove.add(key);
        }
      }
      keysToRemove.forEach(activeInstanceFieldValues::remove);
    } else if (instruction.isStaticPut()) {
      if (field.holder != code.method.holder()) {
        // Accessing a static field on a different object could cause <clinit> to run which
        // could modify any static field on any other object.
        activeStaticFieldValues.clear();
      } else {
        activeStaticFieldValues.remove(field);
      }
    } else if (instruction.isStaticGet()) {
      if (field.holder != code.method.holder()) {
        // Accessing a static field on a different object could cause <clinit> to run which
        // could modify any static field on any other object.
        activeStaticFieldValues.clear();
      }
    } else if (instruction.isInstanceGet()) {
      throw new Unreachable();
    }
  }

  // If a field get instruction throws an exception it did not have an effect on the
  // value of the field. Therefore, when propagating across exceptional edges for a
  // field get instruction we have to exclude that field from the set of known
  // field values.
  private void killActiveFieldsForExceptionalExit(FieldInstruction instruction) {
    DexField field = instruction.getField();
    if (instruction.isInstanceGet()) {
      Value object = instruction.asInstanceGet().object().getAliasedValue();
      FieldAndObject fieldAndObject = new FieldAndObject(field, object);
      activeInstanceFieldValues.remove(fieldAndObject);
    } else if (instruction.isStaticGet()) {
      activeStaticFieldValues.remove(field);
    }
  }

  private void killActiveInitializedClassesForExceptionalExit(InitClass instruction) {
    activeInitializedClasses.remove(instruction.getClassValue());
  }
}
