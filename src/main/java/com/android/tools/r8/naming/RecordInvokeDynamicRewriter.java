// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.records.RecordCfToCfRewriter;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** Rewrites the record invokedynamic in hashCode, equals and toString. */
public class RecordInvokeDynamicRewriter {

  private final AppView<?> appView;
  private final RecordCfToCfRewriter recordCfToCfRewriter;
  private final NamingLens lens;

  public RecordInvokeDynamicRewriter(AppView<?> appView, NamingLens lens) {
    this.appView = appView;
    this.recordCfToCfRewriter = RecordCfToCfRewriter.create(appView);
    this.lens = lens;
  }

  public void run(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          clazz.forEachProgramMethodMatching(
              DexEncodedMethod::hasCode, this::rewriteRecordInvokeDynamicInMethod);
        },
        executorService);
  }

  private void rewriteRecordInvokeDynamicInMethod(ProgramMethod programMethod) {
    if (recordCfToCfRewriter == null) {
      return;
    }
    if (!programMethod.getHolder().isRecord()) {
      return;
    }
    Code code = programMethod.getDefinition().getCode();
    assert code != null;
    if (code.isDexCode()) {
      return;
    }
    List<CfInstruction> instructions = code.asCfCode().getInstructions();
    List<CfInstruction> newInstructions =
        ListUtils.mapOrElse(
            instructions,
            (int index, CfInstruction instruction) -> {
              if (instruction.isInvokeDynamic()) {
                return recordCfToCfRewriter.rewriteRecordInvokeDynamic(
                    instruction.asInvokeDynamic(), programMethod, lens);
              }
              return instruction;
            },
            instructions);
    code.asCfCode().setInstructions(newInstructions);
  }
}
