// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;

import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.ConstStringJumbo;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemBasedString;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardClassFilter;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.Map;

class IdentifierMinifier {

  private final AppInfoWithLiveness appInfo;
  private final ProguardClassFilter adaptClassStrings;
  private final NamingLens lens;
  private final Object2BooleanMap<DexReference> identifierNameStrings;

  IdentifierMinifier(
      AppInfoWithLiveness appInfo, ProguardClassFilter adaptClassStrings, NamingLens lens) {
    this.appInfo = appInfo;
    this.adaptClassStrings = adaptClassStrings;
    this.lens = lens;
    this.identifierNameStrings = appInfo.identifierNameStrings;
  }

  void run() {
    if (!adaptClassStrings.isEmpty()) {
      adaptClassStrings();
    }
    if (!identifierNameStrings.isEmpty()) {
      replaceIdentifierNameString();
    }
  }

  private void adaptClassStrings() {
    for (DexProgramClass clazz : appInfo.classes()) {
      if (!adaptClassStrings.matches(clazz.type)) {
        continue;
      }
      clazz.forEachField(this::adaptClassStringsInField);
      clazz.forEachMethod(this::adaptClassStringsInMethod);
    }
  }

  private void adaptClassStringsInField(DexEncodedField encodedField) {
    if (!encodedField.accessFlags.isStatic()) {
      return;
    }
    DexValue staticValue = encodedField.getStaticValue();
    if (!(staticValue instanceof DexValueString)) {
      return;
    }
    DexString original = ((DexValueString) staticValue).getValue();
    DexString renamed = getRenamedStringLiteral(original);
    if (renamed != original) {
      encodedField.setStaticValue(new DexValueString(renamed));
    }
  }

  private void adaptClassStringsInMethod(DexEncodedMethod encodedMethod) {
    // Abstract methods do not have code_item.
    if (encodedMethod.accessFlags.isAbstract()) {
      return;
    }
    Code code = encodedMethod.getCode();
    if (code == null) {
      return;
    }
    if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      for (Instruction instr : dexCode.instructions) {
        if (instr instanceof ConstString) {
          ConstString cnst = (ConstString) instr;
          DexString dexString = cnst.getString();
          cnst.BBBB = getRenamedStringLiteral(dexString);
        } else if (instr instanceof ConstStringJumbo) {
          ConstStringJumbo cnst = (ConstStringJumbo) instr;
          DexString dexString = cnst.getString();
          cnst.BBBBBBBB = getRenamedStringLiteral(dexString);
        }
      }
    } else {
      assert code.isCfCode();
      CfCode cfCode = code.asCfCode();

      for (CfInstruction instr : cfCode.getInstructions()) {
        if (instr instanceof CfConstString) {
          CfConstString cnst = (CfConstString) instr;
          DexString dexString = cnst.getString();
          cnst.setString(getRenamedStringLiteral(dexString));
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

  private void replaceIdentifierNameString() {
    for (DexProgramClass clazz : appInfo.classes()) {
      // Some const strings could be moved to field's static value (from <clinit>).
      clazz.forEachField(this::replaceIdentifierNameStringInField);
      clazz.forEachMethod(this::replaceIdentifierNameStringInMethod);
    }
  }

  private void replaceIdentifierNameStringInField(DexEncodedField encodedField) {
    if (!encodedField.accessFlags.isStatic()) {
      return;
    }
    DexValue staticValue = encodedField.getStaticValue();
    if (!(staticValue instanceof DexValueString)) {
      return;
    }
    DexString original = ((DexValueString) staticValue).getValue();
    if (original instanceof DexItemBasedString) {
      encodedField.setStaticValue(new DexValueString(materialize((DexItemBasedString) original)));
    }
  }

  private void replaceIdentifierNameStringInMethod(DexEncodedMethod encodedMethod) {
    if (!encodedMethod.getOptimizationInfo().useIdentifierNameString()) {
      return;
    }
    // Abstract methods do not have code_item.
    if (encodedMethod.accessFlags.isAbstract()) {
      return;
    }
    Code code = encodedMethod.getCode();
    if (code == null) {
      return;
    }
    if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      for (Instruction instr : dexCode.instructions) {
        if (instr instanceof ConstString
            && ((ConstString) instr).getString() instanceof DexItemBasedString) {
          ConstString cnst = (ConstString) instr;
          DexItemBasedString itemBasedString = (DexItemBasedString) cnst.getString();
          cnst.BBBB = materialize(itemBasedString);
        } else if (instr instanceof ConstStringJumbo
            && ((ConstStringJumbo) instr).getString() instanceof DexItemBasedString) {
          ConstStringJumbo cnst = (ConstStringJumbo) instr;
          DexItemBasedString itemBasedString = (DexItemBasedString) cnst.getString();
          cnst.BBBBBBBB = materialize(itemBasedString);
        }
      }
    } else {
      assert code.isCfCode();
      CfCode cfCode = code.asCfCode();

      for (CfInstruction instr : cfCode.getInstructions()) {
        if (instr instanceof CfConstString
            && ((CfConstString) instr).getString() instanceof DexItemBasedString) {
          CfConstString cnst = (CfConstString) instr;
          DexItemBasedString itemBasedString = (DexItemBasedString) cnst.getString();
          cnst.setString(materialize(itemBasedString));
        }
      }
    }
  }

  private DexString materialize(DexItemBasedString itemBasedString) {
    if (itemBasedString.basedOn.isDexType()) {
      DexString renamed = lens.lookupDescriptor(itemBasedString.basedOn.asDexType());
      if (!renamed.toString().equals(itemBasedString.toString())) {
        return appInfo.dexItemFactory.createString(descriptorToJavaType(renamed.toString()));
      }
      return renamed;
    } else if (itemBasedString.basedOn.isDexMethod()) {
      return lens.lookupName(itemBasedString.basedOn.asDexMethod());
    } else {
      assert itemBasedString.basedOn.isDexField();
      return lens.lookupName(itemBasedString.basedOn.asDexField());
    }
  }
}
