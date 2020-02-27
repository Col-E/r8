// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.SwitchMapCollector;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.AppInfoWithLiveness.EnumValueInfo;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.Arrays;
import java.util.Map;

public class EnumValueOptimizer {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;

  public EnumValueOptimizer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  @SuppressWarnings("ConstantConditions")
  public void rewriteConstantEnumMethodCalls(IRCode code) {
    if (!code.metadata().mayHaveInvokeMethodWithReceiver()) {
      return;
    }

    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();

      if (!current.isInvokeMethodWithReceiver()) {
        continue;
      }
      InvokeMethodWithReceiver methodWithReceiver = current.asInvokeMethodWithReceiver();
      DexMethod invokedMethod = methodWithReceiver.getInvokedMethod();
      boolean isOrdinalInvoke = invokedMethod == factory.enumMethods.ordinal;
      boolean isNameInvoke = invokedMethod == factory.enumMethods.name;
      boolean isToStringInvoke = invokedMethod == factory.enumMethods.toString;
      if (!isOrdinalInvoke && !isNameInvoke && !isToStringInvoke) {
        continue;
      }

      Value receiver = methodWithReceiver.getReceiver().getAliasedValue();
      if (receiver.isPhi()) {
        continue;
      }
      Instruction definition = receiver.getDefinition();
      if (!definition.isStaticGet()) {
        continue;
      }
      DexField enumField = definition.asStaticGet().getField();

      Map<DexField, EnumValueInfo> valueInfoMap =
          appView.appInfo().withLiveness().getEnumValueInfoMapFor(enumField.type);
      if (valueInfoMap == null) {
        continue;
      }

      // The receiver value is identified as being from a constant enum field lookup by the fact
      // that it is a static-get to a field whose type is the same as the enclosing class (which
      // is known to be an enum type). An enum may still define a static field using the enum type
      // so ensure the field is present in the ordinal map for final validation.
      EnumValueInfo valueInfo = valueInfoMap.get(enumField);
      if (valueInfo == null) {
        continue;
      }

      Value outValue = methodWithReceiver.outValue();
      if (isOrdinalInvoke) {
        iterator.replaceCurrentInstruction(new ConstNumber(outValue, valueInfo.ordinal));
      } else if (isNameInvoke) {
        iterator.replaceCurrentInstruction(
            new ConstString(outValue, enumField.name, ThrowingInfo.NO_THROW));
      } else {
        assert isToStringInvoke;
        DexClass enumClazz = appView.appInfo().definitionFor(enumField.type);
        if (!enumClazz.accessFlags.isFinal()) {
          continue;
        }
        DexEncodedMethod singleTarget =
            appView
                .appInfo()
                .resolveMethodOnClass(valueInfo.type, factory.objectMethods.toString)
                .getSingleTarget();
        if (singleTarget != null && singleTarget.method != factory.enumMethods.toString) {
          continue;
        }
        iterator.replaceCurrentInstruction(
            new ConstString(outValue, enumField.name, ThrowingInfo.NO_THROW));
      }
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
    for (BasicBlock block : code.blocks) {
      JumpInstruction exit = block.exit();
      // Pattern match a switch on a switch map as input.
      if (!exit.isIntSwitch()) {
        continue;
      }
      IntSwitch switchInsn = exit.asIntSwitch();
      EnumSwitchInfo info = analyzeSwitchOverEnum(switchInsn);
      if (info == null) {
        continue;
      }
      Int2IntMap targetMap = new Int2IntArrayMap();
      for (int i = 0; i < switchInsn.numberOfKeys(); i++) {
        assert switchInsn.targetBlockIndices()[i] != switchInsn.getFallthroughBlockIndex();
        int key = switchInsn.getKey(i);
        DexField field = info.indexMap.get(key);
        EnumValueInfo valueInfo = info.valueInfoMap.get(field);
        targetMap.put(valueInfo.ordinal, switchInsn.targetBlockIndices()[i]);
      }
      int[] keys = targetMap.keySet().toIntArray();
      Arrays.sort(keys);
      int[] targets = new int[keys.length];
      for (int i = 0; i < keys.length; i++) {
        targets[i] = targetMap.get(keys[i]);
      }

      IntSwitch newSwitch =
          new IntSwitch(
              info.ordinalInvoke.outValue(), keys, targets, switchInsn.getFallthroughBlockIndex());
      // Replace the switch itself.
      exit.replace(newSwitch, code);
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
  }

  private static final class EnumSwitchInfo {

    final DexType enumClass;
    final Instruction ordinalInvoke;
    final Instruction arrayGet;
    public final Instruction staticGet;
    final Int2ReferenceMap<DexField> indexMap;
    final Map<DexField, EnumValueInfo> valueInfoMap;

    private EnumSwitchInfo(
        DexType enumClass,
        Instruction ordinalInvoke,
        Instruction arrayGet,
        Instruction staticGet,
        Int2ReferenceMap<DexField> indexMap,
        Map<DexField, EnumValueInfo> valueInfoMap) {
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
   * and extracts the components and the index and ordinal maps. See {@link EnumInfoMapCollector}
   * and {@link SwitchMapCollector} for details.
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
    Int2ReferenceMap<DexField> indexMap = appView.appInfo().getSwitchMapFor(staticGet.getField());
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
    Map<DexField, EnumValueInfo> valueInfoMap = appView.appInfo().getEnumValueInfoMapFor(enumType);
    if (valueInfoMap == null) {
      return null;
    }
    return new EnumSwitchInfo(enumType, ordinalInvoke, arrayGet, staticGet, indexMap, valueInfoMap);
  }
}
