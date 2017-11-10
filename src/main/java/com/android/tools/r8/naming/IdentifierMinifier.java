// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;

import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.ConstStringJumbo;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemBasedString;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardClassFilter;
import java.util.Map;
import java.util.Set;

class IdentifierMinifier {

  private final AppInfoWithLiveness appInfo;
  private final ProguardClassFilter adaptClassStrings;
  private final NamingLens lens;
  private final Set<DexItem> identifierNameStrings;

  IdentifierMinifier(
      AppInfoWithLiveness appInfo,
      ProguardClassFilter adaptClassStrings,
      NamingLens lens) {
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
      for (DexEncodedField encodedField : clazz.allFieldsSorted()) {
        if (encodedField.staticValue instanceof DexValueString) {
          DexString original = ((DexValueString) encodedField.staticValue).getValue();
          DexString renamed = getRenamedStringLiteral(original);
          if (renamed != original) {
            encodedField.staticValue = new DexValueString(renamed);
          }
        }
      }
      for (DexEncodedMethod encodedMethod : clazz.allMethodsSorted()) {
        // Abstract methods do not have code_item.
        if (encodedMethod.accessFlags.isAbstract()) {
          continue;
        }
        Code code = encodedMethod.getCode();
        if (code == null) {
          continue;
        }
        assert code.isDexCode();
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
      for (DexEncodedMethod encodedMethod : clazz.allMethodsSorted()) {
        if (!encodedMethod.getOptimizationInfo().useIdentifierNameString()) {
          continue;
        }
        Code code = encodedMethod.getCode();
        if (code == null) {
          continue;
        }
        assert code.isDexCode();
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
      }
    }
    // Some const strings could be moved to field's static value (from <clinit>).
    for (DexItem dexItem : identifierNameStrings) {
      if (!(dexItem instanceof DexField)) {
        continue;
      }
      DexEncodedField encodedField = appInfo.definitionFor((DexField) dexItem);
      if (!(encodedField.staticValue instanceof DexValueString)) {
        continue;
      }
      DexString original = ((DexValueString) encodedField.staticValue).getValue();
      if (original instanceof DexItemBasedString) {
        encodedField.staticValue = new DexValueString(materialize((DexItemBasedString) original));
      }
    }
  }

  private DexString materialize(DexItemBasedString itemBasedString) {
    if (itemBasedString.basedOn instanceof DexType) {
      DexString renamed = lens.lookupDescriptor((DexType) itemBasedString.basedOn);
      if (!renamed.toString().equals(itemBasedString.toString())) {
        return appInfo.dexItemFactory.createString(descriptorToJavaType(renamed.toString()));
      }
      return renamed;
    } else if (itemBasedString.basedOn instanceof DexMethod) {
      return lens.lookupName((DexMethod) itemBasedString.basedOn);
    } else {
      assert itemBasedString.basedOn instanceof DexField;
      return lens.lookupName((DexField) itemBasedString.basedOn);
    }
  }

}
