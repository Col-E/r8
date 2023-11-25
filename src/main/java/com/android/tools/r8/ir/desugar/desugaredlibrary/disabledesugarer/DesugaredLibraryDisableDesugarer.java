// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.disabledesugarer;

import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfTypeInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaring;
import com.android.tools.r8.ir.desugar.DesugarDescription;
import com.google.common.collect.ImmutableList;

/**
 * Disables the rewriting of types in specific classes declared in the desugared library
 * specification, typically classes that are used pre-native multidex.
 */
public class DesugaredLibraryDisableDesugarer implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final DesugaredLibraryDisableDesugarerHelper helper;

  public DesugaredLibraryDisableDesugarer(AppView<?> appView) {
    this.appView = appView;
    this.helper = new DesugaredLibraryDisableDesugarerHelper(appView);
  }

  public static DesugaredLibraryDisableDesugarer create(AppView<?> appView) {
    return DesugaredLibraryDisableDesugarerHelper.shouldCreate(appView)
        ? new DesugaredLibraryDisableDesugarer(appView)
        : null;
  }

  @Override
  public DesugarDescription compute(CfInstruction instruction, ProgramMethod context) {
    CfInstruction replacement = rewriteInstruction(instruction, context);
    if (replacement == null) {
      return DesugarDescription.nothing();
    }
    return compute(replacement);
  }

  private DesugarDescription compute(CfInstruction replacement) {
    return DesugarDescription.builder()
        .setDesugarRewrite(
            (freshLocalProvider,
                localStackAllocator,
                desugaringInfo,
                eventConsumer,
                context,
                methodProcessingContext,
                desugaringCollection,
                dexItemFactory) -> ImmutableList.of(replacement))
        .build();
  }

  // TODO(b/261024278): Share this code.
  private CfInstruction rewriteInstruction(CfInstruction instruction, ProgramMethod context) {
    if (!appView.dexItemFactory().multiDexTypes.contains(context.getHolderType())) {
      return null;
    }
    if (instruction.isTypeInstruction()) {
      return rewriteTypeInstruction(instruction.asTypeInstruction());
    }
    if (instruction.isFieldInstruction()) {
      return rewriteFieldInstruction(instruction.asFieldInstruction(), context);
    }
    if (instruction.isInvoke()) {
      return rewriteInvokeInstruction(instruction.asInvoke(), context);
    }
    return null;
  }

  private CfInstruction rewriteInvokeInstruction(CfInvoke invoke, ProgramMethod context) {
    DexMethod rewrittenMethod =
        helper.rewriteMethod(invoke.getMethod(), invoke.isInterface(), context);
    return rewrittenMethod != null
        ? new CfInvoke(invoke.getOpcode(), rewrittenMethod, invoke.isInterface())
        : null;
  }

  private CfFieldInstruction rewriteFieldInstruction(
      CfFieldInstruction fieldInstruction, ProgramMethod context) {
    DexField rewrittenField = helper.rewriteField(fieldInstruction.getField(), context);
    return rewrittenField != null ? fieldInstruction.createWithField(rewrittenField) : null;
  }

  @SuppressWarnings("ReferenceEquality")
  private CfInstruction rewriteTypeInstruction(CfTypeInstruction typeInstruction) {
    DexType rewrittenType = helper.rewriteType(typeInstruction.getType());
    return rewrittenType != typeInstruction.getType()
        ? typeInstruction.withType(rewrittenType)
        : null;
  }
}
