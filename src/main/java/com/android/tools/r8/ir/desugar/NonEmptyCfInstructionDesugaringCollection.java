// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;


import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialToSelfDesugaring;
import com.android.tools.r8.ir.desugar.lambda.LambdaInstructionDesugaring;
import com.android.tools.r8.ir.desugar.nest.D8NestBasedAccessDesugaring;
import com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring;
import com.android.tools.r8.ir.desugar.stringconcat.StringConcatInstructionDesugaring;
import com.android.tools.r8.ir.desugar.twr.TwrCloseResourceInstructionDesugaring;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class NonEmptyCfInstructionDesugaringCollection extends CfInstructionDesugaringCollection {

  private final AppView<?> appView;
  private final List<CfInstructionDesugaring> desugarings = new ArrayList<>();

  private final NestBasedAccessDesugaring nestBasedAccessDesugaring;

  NonEmptyCfInstructionDesugaringCollection(AppView<?> appView) {
    this.appView = appView;
    this.nestBasedAccessDesugaring = NestBasedAccessDesugaring.create(appView);
    desugarings.add(new LambdaInstructionDesugaring(appView));
    desugarings.add(new InvokeSpecialToSelfDesugaring(appView));
    desugarings.add(new StringConcatInstructionDesugaring(appView));
    if (appView.options().enableTryWithResourcesDesugaring()) {
      desugarings.add(new TwrCloseResourceInstructionDesugaring(appView));
    }
    if (nestBasedAccessDesugaring != null) {
      desugarings.add(nestBasedAccessDesugaring);
    }
  }

  // TODO(b/145775365): special constructor for cf-to-cf compilations with desugaring disabled.
  //  This should be removed once we can represent invoke-special instructions in the IR.
  private NonEmptyCfInstructionDesugaringCollection(
      AppView<?> appView, InvokeSpecialToSelfDesugaring invokeSpecialToSelfDesugaring) {
    this.appView = appView;
    this.nestBasedAccessDesugaring = null;
    desugarings.add(invokeSpecialToSelfDesugaring);
  }

  static NonEmptyCfInstructionDesugaringCollection createForCfToCfNonDesugar(AppView<?> appView) {
    assert appView.options().desugarState.isOff();
    assert appView.options().isGeneratingClassFiles();
    return new NonEmptyCfInstructionDesugaringCollection(
        appView, new InvokeSpecialToSelfDesugaring(appView));
  }

  @Override
  public void desugar(
      ProgramMethod method,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringEventConsumer eventConsumer) {
    Code code = method.getDefinition().getCode();
    if (!code.isCfCode()) {
      appView
          .options()
          .reporter
          .error(
              new StringDiagnostic(
                  "Unsupported attempt to desugar non-CF code",
                  method.getOrigin(),
                  method.getPosition()));
      return;
    }

    CfCode cfCode = code.asCfCode();

    // Tracking of temporary locals used for instruction desugaring. The desugaring of each
    // instruction is assumed to use locals only for the duration of the instruction, such that any
    // temporarily used locals will be free again at the next instruction to be desugared.
    IntBox maxLocalsForCode = new IntBox(cfCode.getMaxLocals());
    IntBox maxLocalsForInstruction = new IntBox(cfCode.getMaxLocals());

    List<CfInstruction> desugaredInstructions =
        ListUtils.flatMap(
            cfCode.getInstructions(),
            instruction -> {
              Collection<CfInstruction> replacement =
                  desugarInstruction(
                      instruction,
                      maxLocalsForInstruction::getAndIncrement,
                      eventConsumer,
                      method,
                      methodProcessingContext);
              if (replacement != null) {
                // Record if we increased the max number of locals for the method, and reset the
                // next temporary locals register.
                maxLocalsForCode.setMax(maxLocalsForInstruction.getAndSet(cfCode.getMaxLocals()));
              } else {
                // The next temporary locals register should be unchanged.
                assert maxLocalsForInstruction.get() == cfCode.getMaxLocals();
              }
              return replacement;
            },
            null);
    if (desugaredInstructions != null) {
      assert maxLocalsForCode.get() >= cfCode.getMaxLocals();
      cfCode.setInstructions(desugaredInstructions);
      cfCode.setMaxLocals(maxLocalsForCode.get());
    } else {
      assert false : "Expected code to be desugared";
    }
  }

  private Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext) {
    // TODO(b/177810578): Migrate other cf-to-cf based desugaring here.
    Iterator<CfInstructionDesugaring> iterator = desugarings.iterator();
    while (iterator.hasNext()) {
      CfInstructionDesugaring desugaring = iterator.next();
      Collection<CfInstruction> replacement =
          desugaring.desugarInstruction(
              instruction, freshLocalProvider, eventConsumer, context, methodProcessingContext);
      if (replacement != null) {
        assert verifyNoOtherDesugaringNeeded(
            instruction, context, methodProcessingContext, iterator);
        return replacement;
      }
    }
    return null;
  }

  @Override
  public boolean needsDesugaring(ProgramMethod method) {
    if (!method.getDefinition().hasCode()) {
      return false;
    }

    Code code = method.getDefinition().getCode();
    if (code.isDexCode()) {
      return false;
    }

    if (!code.isCfCode()) {
      throw new Unreachable("Unexpected attempt to determine if non-CF code needs desugaring");
    }

    return Iterables.any(
        code.asCfCode().getInstructions(), instruction -> needsDesugaring(instruction, method));
  }

  private boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    return Iterables.any(
        desugarings, desugaring -> desugaring.needsDesugaring(instruction, context));
  }

  private static boolean verifyNoOtherDesugaringNeeded(
      CfInstruction instruction,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      Iterator<CfInstructionDesugaring> iterator) {
    assert IteratorUtils.nextUntil(
            iterator,
            desugaring ->
                desugaring.desugarInstruction(
                        instruction,
                        requiredRegisters -> {
                          assert false;
                          return 0;
                        },
                        CfInstructionDesugaringEventConsumer.createForDesugaredCode(),
                        context,
                        methodProcessingContext)
                    != null)
        == null;
    return true;
  }

  @Override
  public <T extends Throwable> void withD8NestBasedAccessDesugaring(
      ThrowingConsumer<D8NestBasedAccessDesugaring, T> consumer) throws T {
    if (nestBasedAccessDesugaring != null) {
      assert nestBasedAccessDesugaring instanceof D8NestBasedAccessDesugaring;
      consumer.accept((D8NestBasedAccessDesugaring) nestBasedAccessDesugaring);
    }
  }
}
