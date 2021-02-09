// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.invokespecial.InvokeSpecialToSelfDesugaring;
import com.android.tools.r8.ir.desugar.nest.NestBasedAccessDesugaring;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class NonEmptyCfInstructionDesugaringCollection extends CfInstructionDesugaringCollection {

  private final AppView<?> appView;
  private final List<CfInstructionDesugaring> desugarings = new ArrayList<>();
  private final InvokeSpecialToSelfDesugaring invokeSpecialToSelfDesugaring;
  private final NestBasedAccessDesugaring nestBasedAccessDesugaring;

  public NonEmptyCfInstructionDesugaringCollection(AppView<?> appView) {
    this.appView = appView;
    this.invokeSpecialToSelfDesugaring = new InvokeSpecialToSelfDesugaring(appView);
    this.nestBasedAccessDesugaring =
        appView.options().shouldDesugarNests() ? new NestBasedAccessDesugaring(appView) : null;
    registerIfNotNull(invokeSpecialToSelfDesugaring);
    registerIfNotNull(nestBasedAccessDesugaring);
  }

  private void registerIfNotNull(CfInstructionDesugaring desugaring) {
    if (desugaring != null) {
      desugarings.add(desugaring);
    }
  }

  @Override
  public void desugar(ProgramMethod method, CfInstructionDesugaringEventConsumer consumer) {
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
    List<CfInstruction> desugaredInstructions =
        ListUtils.flatMap(
            cfCode.getInstructions(),
            instruction -> desugarInstruction(instruction, consumer, method),
            null);
    if (desugaredInstructions != null) {
      cfCode.setInstructions(desugaredInstructions);
    } else {
      assert false : "Expected code to be desugared";
    }
  }

  private List<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      CfInstructionDesugaringEventConsumer consumer,
      ProgramMethod context) {
    // TODO(b/177810578): Migrate other cf-to-cf based desugaring here.
    Iterator<CfInstructionDesugaring> iterator = desugarings.iterator();
    while (iterator.hasNext()) {
      CfInstructionDesugaring desugaring = iterator.next();
      List<CfInstruction> replacement =
          desugaring.desugarInstruction(instruction, consumer, context);
      if (replacement != null) {
        assert verifyNoOtherDesugaringNeeded(instruction, context, iterator);
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
      Iterator<CfInstructionDesugaring> iterator) {
    assert IteratorUtils.nextUntil(
            iterator,
            desugaring ->
                desugaring.desugarInstruction(
                        instruction,
                        CfInstructionDesugaringEventConsumer.createForDesugaredCode(),
                        context)
                    != null)
        == null;
    return true;
  }
}
