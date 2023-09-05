// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;

import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfDexItemBasedConstString;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexItemBasedConstString;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexItemBasedValueString;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.records.RecordCfToCfRewriter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardClassFilter;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Replaces all instances of DexItemBasedConstString by ConstString, and all instances of
 * DexItemBasedValueString by DexValueString.
 */
class IdentifierMinifier {

  private final AppView<AppInfoWithLiveness> appView;
  private final ProguardClassFilter adaptClassStrings;
  private final RecordCfToCfRewriter recordCfToCfRewriter;
  private final NamingLens lens;

  IdentifierMinifier(AppView<AppInfoWithLiveness> appView, NamingLens lens) {
    this.appView = appView;
    this.adaptClassStrings = appView.options().getProguardConfiguration().getAdaptClassStrings();
    this.recordCfToCfRewriter = RecordCfToCfRewriter.create(appView);
    this.lens = lens;
  }

  void run(ExecutorService executorService) throws ExecutionException {
    if (!adaptClassStrings.isEmpty()) {
      adaptClassStrings(executorService);
    }
    replaceDexItemBasedConstString(executorService);
  }

  private void adaptClassStrings(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          if (adaptClassStrings.matches(clazz.type)) {
            for (DexEncodedField field : clazz.staticFields()) {
              adaptClassStringsInStaticField(field);
            }
            clazz.forEachMethod(this::adaptClassStringsInMethod);
          }
        },
        executorService
    );
  }

  private void adaptClassStringsInStaticField(DexEncodedField encodedField) {
    assert encodedField.accessFlags.isStatic();
    DexValue staticValue = encodedField.getStaticValue();
    if (staticValue.isDexValueString()) {
      DexString original = staticValue.asDexValueString().getValue();
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
      DexInstruction[] instructions = code.asDexCode().instructions;
      DexInstruction[] newInstructions =
          ArrayUtils.map(
              instructions,
              (DexInstruction instruction) -> {
                if (instruction.isConstString()) {
                  DexConstString cnst = instruction.asConstString();
                  DexString renamedStringLiteral = getRenamedStringLiteral(cnst.getString());
                  if (!renamedStringLiteral.equals(cnst.getString())) {
                    DexConstString dexConstString =
                        new DexConstString(instruction.asConstString().AA, renamedStringLiteral);
                    dexConstString.setOffset(instruction.getOffset());
                    return dexConstString;
                  }
                }
                return instruction;
              },
              DexInstruction.EMPTY_ARRAY);
      if (instructions != newInstructions) {
        encodedMethod.setCode(
            code.asDexCode().withNewInstructions(newInstructions),
            encodedMethod.getParameterInfo());
      }
    } else if (code.isCfCode()) {
      for (CfInstruction instruction : code.asCfCode().getInstructions()) {
        if (instruction.isConstString()) {
          CfConstString cnst = instruction.asConstString();
          cnst.setString(getRenamedStringLiteral(cnst.getString()));
        }
      }
    } else {
      assert code.isCfWritableCode() || code.isDexWritableCode();
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private DexString getRenamedStringLiteral(DexString originalLiteral) {
    String descriptor =
        DescriptorUtils.javaTypeToDescriptorIfValidJavaType(originalLiteral.toString());
    if (descriptor == null) {
      return originalLiteral;
    }
    DexType type = appView.dexItemFactory().createType(descriptor);
    DexType originalType = appView.graphLens().getOriginalType(type);
    if (originalType != type) {
      // The type has changed to something clashing with the string.
      return originalLiteral;
    }
    DexType rewrittenType = appView.graphLens().lookupType(type);
    DexClass clazz = appView.appInfo().definitionForWithoutExistenceAssert(rewrittenType);
    if (clazz == null || clazz.isNotProgramClass()) {
      return originalLiteral;
    }
    DexString rewrittenString = lens.lookupClassDescriptor(rewrittenType);
    return rewrittenString == null
        ? originalLiteral
        : appView.dexItemFactory().createString(descriptorToJavaType(rewrittenString.toString()));
  }

  void replaceDexItemBasedConstString(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          // Some const strings could be moved to field's static value (from <clinit>).
          for (DexEncodedField field : clazz.staticFields()) {
            replaceDexItemBasedConstStringInStaticField(field);
          }
          clazz.forEachProgramMethodMatching(
              DexEncodedMethod::hasCode, this::replaceDexItemBasedConstStringInMethod);
        },
        executorService);
  }

  private void replaceDexItemBasedConstStringInStaticField(DexEncodedField encodedField) {
    assert encodedField.accessFlags.isStatic();
    DexValue staticValue = encodedField.getStaticValue();
    if (staticValue instanceof DexItemBasedValueString) {
      DexItemBasedValueString cnst = (DexItemBasedValueString) staticValue;
      DexString replacement =
          cnst.getNameComputationInfo()
              .computeNameFor(cnst.getValue(), appView, appView.graphLens(), lens);
      encodedField.setStaticValue(new DexValueString(replacement));
    }
  }

  private void replaceDexItemBasedConstStringInMethod(ProgramMethod programMethod) {
    Code code = programMethod.getDefinition().getCode();
    assert code != null;
    if (code.isDexCode()) {
      DexInstruction[] instructions = code.asDexCode().instructions;
      DexInstruction[] newInstructions =
          ArrayUtils.map(
              instructions,
              (DexInstruction instruction) -> {
                if (instruction.isDexItemBasedConstString()) {
                  DexItemBasedConstString cnst = instruction.asDexItemBasedConstString();
                  DexString replacement =
                      cnst.getNameComputationInfo()
                          .computeNameFor(cnst.getItem(), appView, appView.graphLens(), lens);
                  DexConstString constString = new DexConstString(cnst.AA, replacement);
                  constString.setOffset(instruction.getOffset());
                  return constString;
                }
                return instruction;
              },
              DexInstruction.EMPTY_ARRAY);
      if (newInstructions != instructions) {
        programMethod.setCode(code.asDexCode().withNewInstructions(newInstructions), appView);
      }
    } else if (code.isCfCode()) {
      List<CfInstruction> instructions = code.asCfCode().getInstructions();
      List<CfInstruction> newInstructions =
          ListUtils.mapOrElse(
              instructions,
              (int i, CfInstruction instruction) -> {
                if (instruction.isDexItemBasedConstString()) {
                  CfDexItemBasedConstString cnst = instruction.asDexItemBasedConstString();
                  return new CfConstString(
                      cnst.getNameComputationInfo()
                          .computeNameFor(cnst.getItem(), appView, appView.graphLens(), lens));
                } else if (recordCfToCfRewriter != null && instruction.isInvokeDynamic()) {
                  return recordCfToCfRewriter.rewriteRecordInvokeDynamic(
                      instruction.asInvokeDynamic(), programMethod, lens);
                }
                return instruction;
              },
              instructions);
      code.asCfCode().setInstructions(newInstructions);
    } else {
      assert code.isDefaultInstanceInitializerCode() || code.isThrowNullCode();
    }
  }
}
