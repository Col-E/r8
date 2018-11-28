// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.ir.optimize.ReflectionOptimizer.computeClassName;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;

import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfDexItemBasedConstString;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.DexItemBasedConstString;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexItemBasedValueString;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardClassFilter;
import java.util.List;
import java.util.Map;

/**
 * Replaces all instances of DexItemBasedConstString by ConstString, and all instances of
 * DexItemBasedValueString by DexValueString.
 */
class IdentifierMinifier {

  private final AppInfoWithLiveness appInfo;
  private final ProguardClassFilter adaptClassStrings;
  private final NamingLens lens;

  IdentifierMinifier(
      AppInfoWithLiveness appInfo, ProguardClassFilter adaptClassStrings, NamingLens lens) {
    this.appInfo = appInfo;
    this.adaptClassStrings = adaptClassStrings;
    this.lens = lens;
  }

  void run() {
    if (!adaptClassStrings.isEmpty()) {
      adaptClassStrings();
    }
    replaceDexItemBasedConstString();
  }

  private void adaptClassStrings() {
    for (DexProgramClass clazz : appInfo.classes()) {
      if (adaptClassStrings.matches(clazz.type)) {
        for (DexEncodedField field : clazz.staticFields()) {
          adaptClassStringsInStaticField(field);
        }
        clazz.forEachMethod(this::adaptClassStringsInMethod);
      }
    }
  }

  private void adaptClassStringsInStaticField(DexEncodedField encodedField) {
    assert encodedField.accessFlags.isStatic();
    DexValue staticValue = encodedField.getStaticValue();
    if (staticValue instanceof DexValueString) {
      DexString original = ((DexValueString) staticValue).getValue();
      encodedField.setStaticValue(new DexValueString(getRenamedStringLiteral(original)));
    }
  }

  private void adaptClassStringsInMethod(DexEncodedMethod encodedMethod) {
    // Abstract methods do not have code_item.
    if (encodedMethod.shouldNotHaveCode()) {
      return;
    }
    Code code = encodedMethod.getCode();
    if (code == null) {
      return;
    }
    if (code.isDexCode()) {
      for (Instruction instruction : code.asDexCode().instructions) {
        if (instruction.isConstString()) {
          ConstString cnst = instruction.asConstString();
          cnst.BBBB = getRenamedStringLiteral(cnst.getString());
        }
      }
    } else {
      assert code.isCfCode();
      for (CfInstruction instruction : code.asCfCode().getInstructions()) {
        if (instruction.isConstString()) {
          CfConstString cnst = instruction.asConstString();
          cnst.setString(getRenamedStringLiteral(cnst.getString()));
        }
      }
    }
  }

  private DexString getRenamedStringLiteral(DexString originalLiteral) {
    String originalString = originalLiteral.toString();
    Map<String, DexType> renamedYetMatchedTypes =
        lens.getRenamedItems(
            DexType.class,
            type -> type.toSourceString().equals(originalString),
            DexType::toSourceString);
    DexType type = renamedYetMatchedTypes.get(originalString);
    if (type != null) {
      DexString renamed = lens.lookupDescriptor(type);
      // Create a new DexString only when the corresponding string literal will be replaced.
      if (renamed != originalLiteral) {
        return appInfo.dexItemFactory.createString(descriptorToJavaType(renamed.toString()));
      }
    }
    return originalLiteral;
  }

  private void replaceDexItemBasedConstString() {
    for (DexProgramClass clazz : appInfo.classes()) {
      // Some const strings could be moved to field's static value (from <clinit>).
      for (DexEncodedField field : clazz.staticFields()) {
        replaceDexItemBasedConstStringInStaticField(field);
      }
      clazz.forEachMethod(this::replaceDexItemBasedConstStringInMethod);
    }
  }

  private void replaceDexItemBasedConstStringInStaticField(DexEncodedField encodedField) {
    assert encodedField.accessFlags.isStatic();
    DexValue staticValue = encodedField.getStaticValue();
    if (staticValue instanceof DexItemBasedValueString) {
      DexItemBasedValueString dexItemBasedValueString = (DexItemBasedValueString) staticValue;
      DexReference original = dexItemBasedValueString.getValue();
      DexString replacement =
          dexItemBasedValueString.getClassNameComputationInfo().needsToComputeClassName()
              ? appInfo.dexItemFactory.createString(
                  computeClassName(
                      lens.lookupDescriptor(original.asDexType()),
                      appInfo.definitionFor(original.asDexType()),
                      dexItemBasedValueString.getClassNameComputationInfo()))
              : lens.lookupName(original, appInfo.dexItemFactory);
      encodedField.setStaticValue(new DexValueString(replacement));
    }
  }

  private void replaceDexItemBasedConstStringInMethod(DexEncodedMethod encodedMethod) {
    if (!encodedMethod.getOptimizationInfo().useIdentifierNameString()) {
      return;
    }
    // Abstract methods do not have code_item.
    if (encodedMethod.shouldNotHaveCode()) {
      return;
    }
    Code code = encodedMethod.getCode();
    if (code == null) {
      return;
    }
    if (code.isDexCode()) {
      Instruction[] instructions = code.asDexCode().instructions;
      for (int i = 0; i < instructions.length; ++i) {
        Instruction instruction = instructions[i];
        if (instruction instanceof DexItemBasedConstString) {
          DexItemBasedConstString cnst = instruction.asDexItemBasedConstString();
          DexString replacement =
              cnst.getClassNameComputationInfo().needsToComputeClassName()
                  ? appInfo.dexItemFactory.createString(
                      computeClassName(
                          lens.lookupDescriptor(cnst.getItem().asDexType()),
                          appInfo.definitionFor(cnst.getItem().asDexType()),
                          cnst.getClassNameComputationInfo()))
                  : lens.lookupName(cnst.getItem(), appInfo.dexItemFactory);
          instructions[i] = new ConstString(cnst.AA, replacement);
        }
      }
    } else {
      assert code.isCfCode();
      List<CfInstruction> instructions = code.asCfCode().instructions;
      for (int i = 0; i < instructions.size(); ++i) {
        CfInstruction instruction = instructions.get(i);
        if (instruction.isDexItemBasedConstString()) {
          CfDexItemBasedConstString cnst = instruction.asDexItemBasedConstString();
          DexString replacement =
              cnst.getClassNameComputationInfo().needsToComputeClassName()
                  ? appInfo.dexItemFactory.createString(
                      computeClassName(
                          lens.lookupDescriptor(cnst.getItem().asDexType()),
                          appInfo.definitionFor(cnst.getItem().asDexType()),
                          cnst.getClassNameComputationInfo()))
                  : lens.lookupName(cnst.getItem(), appInfo.dexItemFactory);
          instructions.set(i, new CfConstString(replacement));
        }
      }
    }
  }
}
