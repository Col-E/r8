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
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.analysis.value.SingleStringValue;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ArrayUtils;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.Set;

public class EnumValueOptimizer extends CodeRewriterPass<AppInfoWithLiveness> {

  public EnumValueOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getTimingId() {
    return "EnumValueOptimizer";
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    assert appView.enableWholeProgramOptimizations();
    boolean hasChanged = false;
    AffectedValues affectedValues = new AffectedValues();
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();

      if (current.isInvokeMethodWithReceiver()) {
        InvokeMethodWithReceiver methodWithReceiver = current.asInvokeMethodWithReceiver();
        Value receiver = methodWithReceiver.getReceiver().getAliasedValue();
        if (!receiver.getType().isClassType()
            || !appView()
                .appInfo()
                .isSubtype(
                    receiver.getType().asClassType().getClassType(), dexItemFactory.enumType)) {
          continue;
        }

        DexMethod invokedMethod = methodWithReceiver.getInvokedMethod();
        boolean isOrdinalInvoke = invokedMethod.match(dexItemFactory.enumMembers.ordinalMethod);
        boolean isNameInvoke = invokedMethod.match(dexItemFactory.enumMembers.nameMethod);
        boolean isToStringInvoke = invokedMethod.match(dexItemFactory.enumMembers.toString);
        if (!isOrdinalInvoke && !isNameInvoke && !isToStringInvoke) {
          continue;
        }

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

        FieldOptimizationInfo optimizationInfo = definition.getOptimizationInfo();
        AbstractValue abstractValue = optimizationInfo.getAbstractValue();

        if (methodWithReceiver.hasUnusedOutValue()) {
          // Remove the invoke if it is a call to Enum.name() or Enum.ordinal() as they don't have
          // any side effects. Enum.toString() can be overridden and calls to it could therefore
          // have arbitrary side effects.
          if (methodWithReceiver.getReceiver().getType().isDefinitelyNotNull()
              && !isToStringInvoke) {
            assert isNameInvoke || isOrdinalInvoke;
            iterator.removeOrReplaceByDebugLocalRead();
            hasChanged = true;
          }
          continue;
        }

        Value outValue = methodWithReceiver.outValue();
        if (isOrdinalInvoke) {
          SingleNumberValue ordinalValue =
              getOrdinalValue(code, abstractValue, methodWithReceiver.getReceiver().isNeverNull());
          if (ordinalValue != null) {
            iterator.replaceCurrentInstruction(new ConstNumber(outValue, ordinalValue.getValue()));
            hasChanged = true;
          }
          continue;
        }

        SingleStringValue nameValue =
            getNameValue(code, abstractValue, methodWithReceiver.getReceiver().isNeverNull());
        if (nameValue == null) {
          continue;
        }

        if (isNameInvoke) {
          replaceByName(code, affectedValues, iterator, nameValue);
          hasChanged = true;
          continue;
        }

        assert isToStringInvoke;

        DexClass enumClazz = appView.appInfo().definitionFor(field.type);
        if (!enumClazz.isFinal()) {
          continue;
        }

        // Since the value is a single field value, the type should be exact.
        assert abstractValue.isSingleFieldValue();
        ClassTypeElement enumFieldType = optimizationInfo.getDynamicType().getExactClassType();
        if (enumFieldType == null) {
          assert false : "Expected to have an exact dynamic type for enum instance";
          continue;
        }

        DexEncodedMethod singleTarget =
            appView()
                .appInfo()
                .resolveMethodOnClassLegacy(
                    enumFieldType.getClassType(), dexItemFactory.objectMembers.toString)
                .getSingleTarget();
        if (singleTarget != null
            && singleTarget.getReference() != dexItemFactory.enumMembers.toString) {
          continue;
        }

        replaceByName(code, affectedValues, iterator, nameValue);
        hasChanged = true;
      }
    }
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  private void replaceByName(
      IRCode code,
      Set<Value> affectedValues,
      InstructionListIterator iterator,
      SingleStringValue nameValue) {
    Value newValue = code.createValue(TypeElement.stringClassType(appView, definitelyNotNull()));
    iterator.replaceCurrentInstruction(new ConstString(newValue, nameValue.getDexString()));
    newValue.addAffectedValuesTo(affectedValues);
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    if (!options.enableEnumValueOptimization || !appView.hasLiveness()) {
      return false;
    }
    return code.metadata().mayHaveInvokeMethodWithReceiver();
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
    if (!options.enableEnumValueOptimization || !appView.hasLiveness()) {
      return;
    }
    assert appView.enableWholeProgramOptimizations();
    AffectedValues affectedValues = new AffectedValues();
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

      Int2IntMap ordinalToTargetMap = computeOrdinalToTargetMap(code, switchInsn, info);
      if (ordinalToTargetMap == null) {
        continue;
      }

      int fallthroughBlockIndex = switchInsn.getFallthroughBlockIndex();
      if (ordinalToTargetMap.size() < switchInsn.numberOfKeys()) {
        if (block.numberOfNormalSuccessors() != switchInsn.numberOfKeys() + 1) {
          // This can happen in extremely rare cases where several switch targets are the same
          // block (See b/231804008).
          // TODO(b/249052389): Support removing switch map for such switches.
          continue;
        }
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
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    assert code.isConsistentSSA(appView);
  }

  private Int2IntArrayMap computeOrdinalToTargetMap(
      IRCode code, IntSwitch switchInsn, EnumSwitchInfo info) {
    Int2IntArrayMap ordinalToTargetMap = new Int2IntArrayMap(switchInsn.numberOfKeys());
    for (int i = 0; i < switchInsn.numberOfKeys(); i++) {
      assert switchInsn.targetBlockIndices()[i] != switchInsn.getFallthroughBlockIndex();
      DexField field = info.indexMap.get(switchInsn.getKey(i));
      DexEncodedField enumInstanceField =
          appView.appInfo().resolveField(field, code.context()).getResolvedField();
      if (enumInstanceField == null) {
        // The switch map refers to a field on the enum that does not exist in this compilation.
      } else {
        AbstractValue abstractValue = enumInstanceField.getOptimizationInfo().getAbstractValue();
        // The rewriting effectively leaves in place the myEnum.ordinal() call, so if the value
        // is null, the same NPE happens at runtime, we can assume the value is non null in the
        // switch map after the ordinal call.
        SingleNumberValue ordinalValue = getOrdinalValue(code, abstractValue, true);
        if (ordinalValue == null
            && appView.options().protoShrinking().enableRemoveProtoEnumSwitchMap()) {
          ordinalValue =
              appView
                  .protoShrinker()
                  .protoEnumSwitchMapRemover
                  .getOrdinal(
                      appView.programDefinitionFor(info.enumClass, code.context()),
                      enumInstanceField,
                      appView
                          .appInfo()
                          .resolveField(dexItemFactory.enumMembers.ordinalField, code.context())
                          .getResolvedField());
        }
        if (ordinalValue == null) {
          return null;
        }
        ordinalToTargetMap.put(
            ordinalValue.asSingleNumberValue().getIntValue(), switchInsn.targetBlockIndices()[i]);
      }
    }
    return ordinalToTargetMap;
  }

  private SingleStringValue getNameValue(
      IRCode code, AbstractValue abstractValue, boolean neverNull) {
    AbstractValue ordinalValue =
        getEnumFieldValue(code, abstractValue, dexItemFactory.enumMembers.nameField, neverNull);
    return ordinalValue == null ? null : ordinalValue.asSingleStringValue();
  }

  private SingleNumberValue getOrdinalValue(
      IRCode code, AbstractValue abstractValue, boolean neverNull) {
    AbstractValue ordinalValue =
        getEnumFieldValue(code, abstractValue, dexItemFactory.enumMembers.ordinalField, neverNull);
    return ordinalValue == null ? null : ordinalValue.asSingleNumberValue();
  }

  private AbstractValue getEnumFieldValue(
      IRCode code, AbstractValue abstractValue, DexField field, boolean neverNull) {
    if (neverNull && abstractValue.isNullOrAbstractValue()) {
      abstractValue = abstractValue.asNullOrAbstractValue().getNonNullValue();
    }
    if (!abstractValue.isSingleFieldValue()) {
      return null;
    }
    DexEncodedField encodedField =
        appView.appInfo().resolveField(field, code.context()).getResolvedField();
    if (encodedField == null) {
      return null;
    }
    return abstractValue.asSingleFieldValue().getObjectState().getAbstractFieldValue(encodedField);
  }

  private static final class EnumSwitchInfo {

    final DexType enumClass;
    final Instruction ordinalInvoke;
    final Instruction arrayGet;
    public final Instruction staticGet;
    final Int2ReferenceMap<DexField> indexMap;

    private EnumSwitchInfo(
        DexType enumClass,
        Instruction ordinalInvoke,
        Instruction arrayGet,
        Instruction staticGet,
        Int2ReferenceMap<DexField> indexMap) {
      this.enumClass = enumClass;
      this.ordinalInvoke = ordinalInvoke;
      this.arrayGet = arrayGet;
      this.staticGet = staticGet;
      this.indexMap = indexMap;
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
   * and extracts the components and the index and ordinal maps.
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
    Int2ReferenceMap<DexField> indexMap = appView().appInfo().getSwitchMap(staticGet.getField());
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
    return new EnumSwitchInfo(enumType, ordinalInvoke, arrayGet, staticGet, indexMap);
  }
}
