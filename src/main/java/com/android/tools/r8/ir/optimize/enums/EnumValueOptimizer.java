// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfo;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfoMap;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.ObjectState;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.analysis.value.SingleStringValue;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.SwitchMapCollector;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.Set;

public class EnumValueOptimizer {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;

  public EnumValueOptimizer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  @SuppressWarnings("ConstantConditions")
  public void rewriteConstantEnumMethodCalls(IRCode code) {
    IRMetadata metadata = code.metadata();
    if (!metadata.mayHaveInvokeMethodWithReceiver()
        && !(metadata.mayHaveInvokeStatic() && metadata.mayHaveArrayLength())) {
      return;
    }

    Set<Value> affectedValues = Sets.newIdentityHashSet();
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();

      if (current.isInvokeMethodWithReceiver()) {
        InvokeMethodWithReceiver methodWithReceiver = current.asInvokeMethodWithReceiver();
        DexMethod invokedMethod = methodWithReceiver.getInvokedMethod();
        boolean isOrdinalInvoke = invokedMethod == factory.enumMembers.ordinalMethod;
        boolean isNameInvoke = invokedMethod == factory.enumMembers.nameMethod;
        boolean isToStringInvoke = invokedMethod == factory.enumMembers.toString;
        if (!isOrdinalInvoke && !isNameInvoke && !isToStringInvoke) {
          continue;
        }

        Value receiver = methodWithReceiver.getReceiver().getAliasedValue();
        if (receiver.isPhi()) {
          continue;
        }

        StaticGet staticGet = receiver.getDefinition().asStaticGet();
        if (staticGet == null) {
          continue;
        }

        DexField field = staticGet.getField();
        DexEncodedField definition = field.lookupOnClass(appView.definitionForHolder(field));
        if (definition == null) {
          continue;
        }

        AbstractValue abstractValue = definition.getOptimizationInfo().getAbstractValue();
        if (!abstractValue.isSingleFieldValue()) {
          continue;
        }

        ObjectState objectState = abstractValue.asSingleFieldValue().getState();
        if (objectState.isEmpty()) {
          continue;
        }

        Value outValue = methodWithReceiver.outValue();
        if (isOrdinalInvoke) {
          DexField ordinalField = appView.dexItemFactory().enumMembers.ordinalField;
          DexEncodedField ordinalDefinition =
              ordinalField.lookupOnClass(appView.definitionForHolder(ordinalField));
          if (ordinalDefinition != null) {
            SingleNumberValue ordinalValue =
                objectState.getAbstractFieldValue(ordinalDefinition).asSingleNumberValue();
            if (ordinalValue != null) {
              iterator.replaceCurrentInstruction(
                  new ConstNumber(outValue, ordinalValue.getValue()));
            }
          }
          continue;
        }

        DexField nameField = appView.dexItemFactory().enumMembers.nameField;
        DexEncodedField nameDefinition =
            nameField.lookupOnClass(appView.definitionForHolder(nameField));
        if (nameField == null) {
          continue;
        }

        SingleStringValue nameValue =
            objectState.getAbstractFieldValue(nameDefinition).asSingleStringValue();
        if (nameValue == null) {
          continue;
        }

        if (isNameInvoke) {
          Value newValue =
              code.createValue(TypeElement.stringClassType(appView, definitelyNotNull()));
          iterator.replaceCurrentInstruction(
              new ConstString(
                  newValue,
                  nameValue.getDexString(),
                  ThrowingInfo.defaultForConstString(appView.options())));
          newValue.addAffectedValuesTo(affectedValues);
          continue;
        }

        assert isToStringInvoke;

        DexClass enumClazz = appView.appInfo().definitionFor(field.type);
        if (!enumClazz.isFinal()) {
          continue;
        }

        EnumValueInfo valueInfo = appView.appInfo().withLiveness().getEnumValueInfo(field);
        if (valueInfo == null) {
          continue;
        }

        DexEncodedMethod singleTarget =
            appView
                .appInfo()
                .resolveMethodOnClass(factory.objectMembers.toString, valueInfo.type)
                .getSingleTarget();
        if (singleTarget != null && singleTarget.method != factory.enumMembers.toString) {
          continue;
        }

        Value newValue =
            code.createValue(TypeElement.stringClassType(appView, definitelyNotNull()));
        iterator.replaceCurrentInstruction(
            new ConstString(
                newValue,
                nameValue.getDexString(),
                ThrowingInfo.defaultForConstString(appView.options())));
        newValue.addAffectedValuesTo(affectedValues);
      } else if (current.isArrayLength()) {
        // Rewrites MyEnum.values().length to a constant int.
        Instruction arrayDefinition = current.asArrayLength().array().getAliasedValue().definition;
        if (arrayDefinition != null && arrayDefinition.isInvokeStatic()) {
          DexMethod invokedMethod = arrayDefinition.asInvokeStatic().getInvokedMethod();
          DexProgramClass enumClass = appView.definitionForProgramType(invokedMethod.holder);
          if (enumClass != null
              && enumClass.isEnum()
              && factory.enumMembers.isValuesMethod(invokedMethod, enumClass)) {
            EnumValueInfoMap enumValueInfoMap =
                appView.appInfo().withLiveness().getEnumValueInfoMap(invokedMethod.holder);
            if (enumValueInfoMap != null) {
              iterator.replaceCurrentInstructionWithConstInt(code, enumValueInfoMap.size());
            }
          }
        }
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert code.isConsistentSSA();
  }

  /**
   * Inline the indirection of switch maps into the switch statement.
   *
   * <p>To ensure binary compatibility, javac generated code does not use ordinal values of enums
   * directly in switch statements but instead generates a companion class that computes a mapping
   * from switch branches to ordinals at runtime. As we have whole-program knowledge, we can analyze
   * these maps and inline the indirection into the switch map again.
   *
   * <p>In particular, we look for code of the form
   *
   * <blockquote>
   *
   * <pre>
   * switch(CompanionClass.$switchmap$field[enumValue.ordinal()]) {
   *   ...
   * }
   * </pre>
   *
   * </blockquote>
   */
  public void removeSwitchMaps(IRCode code) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    boolean mayHaveIntroducedUnreachableBlocks = false;
    for (BasicBlock block : code.blocks) {
      IntSwitch switchInsn = block.exit().asIntSwitch();
      // Pattern match a switch on a switch map as input.
      if (switchInsn == null) {
        continue;
      }

      EnumSwitchInfo info = analyzeSwitchOverEnum(switchInsn);
      if (info == null) {
        continue;
      }

      Int2IntMap ordinalToTargetMap = new Int2IntArrayMap(switchInsn.numberOfKeys());
      for (int i = 0; i < switchInsn.numberOfKeys(); i++) {
        assert switchInsn.targetBlockIndices()[i] != switchInsn.getFallthroughBlockIndex();
        DexField field = info.indexMap.get(switchInsn.getKey(i));
        EnumValueInfo valueInfo = info.valueInfoMap.getEnumValueInfo(field);
        if (valueInfo != null) {
          ordinalToTargetMap.put(valueInfo.ordinal, switchInsn.targetBlockIndices()[i]);
        } else {
          // The switch map refers to a field on the enum that does not exist in this compilation.
        }
      }

      int fallthroughBlockIndex = switchInsn.getFallthroughBlockIndex();
      if (ordinalToTargetMap.size() < switchInsn.numberOfKeys()) {
        // There is at least one dead switch case. This can happen when some dependencies use
        // different versions of the same enum.
        int numberOfNormalSuccessors = switchInsn.numberOfKeys() + 1;
        int numberOfExceptionalSuccessors = block.numberOfExceptionalSuccessors();
        IntSet ordinalToTargetValues = new IntOpenHashSet(ordinalToTargetMap.values());

        // Compute which successors that are dead. We don't include the exceptional successors,
        // since none of them are dead. Therefore, `deadBlockIndices[i]` represents if the i'th
        // normal successor is dead, i.e., if the (i+numberOfExceptionalSuccessors)'th successor is
        // dead.
        //
        // Note: we use an int[] to efficiently fixup `ordinalToTargetMap` below.
        int[] deadBlockIndices =
            ArrayUtils.fromPredicate(
                index -> {
                  // It is dead if it is not targeted by a switch case and it is not the fallthrough
                  // block.
                  int adjustedIndex = index + numberOfExceptionalSuccessors;
                  return !ordinalToTargetValues.contains(adjustedIndex)
                      && adjustedIndex != switchInsn.getFallthroughBlockIndex();
                },
                numberOfNormalSuccessors);

        // Detach the dead successors from the graph, and record that we need to remove unreachable
        // blocks in the end.
        IntList successorIndicesToRemove = new IntArrayList(numberOfNormalSuccessors);
        for (int i = 0; i < numberOfNormalSuccessors; i++) {
          if (deadBlockIndices[i] == 1) {
            BasicBlock successor = block.getSuccessors().get(i + numberOfExceptionalSuccessors);
            successor.removePredecessor(block, affectedValues);
            successorIndicesToRemove.add(i);
          }
        }
        block.removeSuccessorsByIndex(successorIndicesToRemove);
        mayHaveIntroducedUnreachableBlocks = true;

        // Fixup `ordinalToTargetMap` and the fallthrough index.
        ArrayUtils.sumOfPredecessorsInclusive(deadBlockIndices);
        for (Int2IntMap.Entry entry : ordinalToTargetMap.int2IntEntrySet()) {
          ordinalToTargetMap.put(
              entry.getIntKey(), entry.getIntValue() - deadBlockIndices[entry.getIntValue()]);
        }
        fallthroughBlockIndex -= deadBlockIndices[fallthroughBlockIndex];
      }

      int[] keys = ordinalToTargetMap.keySet().toIntArray();
      Arrays.sort(keys);
      int[] targets = new int[keys.length];
      for (int i = 0; i < keys.length; i++) {
        targets[i] = ordinalToTargetMap.get(keys[i]);
      }

      IntSwitch newSwitch =
          new IntSwitch(info.ordinalInvoke.outValue(), keys, targets, fallthroughBlockIndex);

      // Replace the switch itself.
      switchInsn.replace(newSwitch, code);

      // If the original input to the switch is now unused, remove it too. It is not dead
      // as it might have side-effects but we ignore these here.
      Instruction arrayGet = info.arrayGet;
      if (!arrayGet.outValue().hasUsers()) {
        arrayGet.inValues().forEach(v -> v.removeUser(arrayGet));
        arrayGet.getBlock().removeInstruction(arrayGet);
      }

      Instruction staticGet = info.staticGet;
      if (!staticGet.outValue().hasUsers()) {
        assert staticGet.inValues().isEmpty();
        staticGet.getBlock().removeInstruction(staticGet);
      }
    }
    if (mayHaveIntroducedUnreachableBlocks) {
      affectedValues.addAll(code.removeUnreachableBlocks());
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
  }

  private static final class EnumSwitchInfo {

    final DexType enumClass;
    final Instruction ordinalInvoke;
    final Instruction arrayGet;
    public final Instruction staticGet;
    final Int2ReferenceMap<DexField> indexMap;
    final EnumValueInfoMap valueInfoMap;

    private EnumSwitchInfo(
        DexType enumClass,
        Instruction ordinalInvoke,
        Instruction arrayGet,
        Instruction staticGet,
        Int2ReferenceMap<DexField> indexMap,
        EnumValueInfoMap valueInfoMap) {
      this.enumClass = enumClass;
      this.ordinalInvoke = ordinalInvoke;
      this.arrayGet = arrayGet;
      this.staticGet = staticGet;
      this.indexMap = indexMap;
      this.valueInfoMap = valueInfoMap;
    }
  }

  /**
   * Looks for a switch statement over the enum companion class of the form
   *
   * <blockquote>
   *
   * <pre>
   * switch(CompanionClass.$switchmap$field[enumValue.ordinal()]) {
   *   ...
   * }
   * </pre>
   *
   * </blockquote>
   *
   * and extracts the components and the index and ordinal maps. See {@link
   * EnumValueInfoMapCollector} and {@link SwitchMapCollector} for details.
   */
  private EnumSwitchInfo analyzeSwitchOverEnum(IntSwitch switchInsn) {
    Instruction input = switchInsn.inValues().get(0).definition;
    if (input == null || !input.isArrayGet()) {
      return null;
    }
    ArrayGet arrayGet = input.asArrayGet();
    Instruction index = arrayGet.index().definition;
    if (index == null || !index.isInvokeVirtual()) {
      return null;
    }
    InvokeVirtual ordinalInvoke = index.asInvokeVirtual();
    DexMethod ordinalMethod = ordinalInvoke.getInvokedMethod();
    DexClass enumClass = appView.definitionFor(ordinalMethod.holder);
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    // After member rebinding, enumClass will be the actual java.lang.Enum class.
    if (enumClass == null
        || (!enumClass.accessFlags.isEnum() && enumClass.type != dexItemFactory.enumType)
        || ordinalMethod.name != dexItemFactory.ordinalMethodName
        || ordinalMethod.proto.returnType != dexItemFactory.intType
        || !ordinalMethod.proto.parameters.isEmpty()) {
      return null;
    }
    Instruction array = arrayGet.array().definition;
    if (array == null || !array.isStaticGet()) {
      return null;
    }
    StaticGet staticGet = array.asStaticGet();
    Int2ReferenceMap<DexField> indexMap = appView.appInfo().getSwitchMap(staticGet.getField());
    if (indexMap == null || indexMap.isEmpty()) {
      return null;
    }
    for (int key : switchInsn.getKeys()) {
      if (!indexMap.containsKey(key)) {
        return null;
      }
    }
    // Due to member rebinding, only the fields are certain to provide the actual enums class.
    DexType enumType = indexMap.values().iterator().next().holder;
    EnumValueInfoMap valueInfoMap = appView.appInfo().getEnumValueInfoMap(enumType);
    if (valueInfoMap == null) {
      return null;
    }
    return new EnumSwitchInfo(enumType, ordinalInvoke, arrayGet, staticGet, indexMap, valueInfoMap);
  }
}
