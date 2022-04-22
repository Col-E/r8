// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractTransferFunction;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.cf.CfBlock;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.cf.CfControlFlowGraph;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.cf.CfIntraproceduralDataflowAnalysis;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class CfOpenClosedInterfacesAnalysis {

  private final AppView<AppInfoWithLiveness> appView;

  public CfOpenClosedInterfacesAnalysis(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void run() {
    // TODO(b/214496607): Parallelize the analysis.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethodMatching(DexEncodedMethod::hasCode, this::processMethod);
    }
  }

  private void processMethod(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (!code.isCfCode()) {
      assert code.isDefaultInstanceInitializerCode() || code.isThrowNullCode();
      return;
    }

    CfCode cfCode = code.asCfCode();
    CfControlFlowGraph cfg = CfControlFlowGraph.create(cfCode);
    CfIntraproceduralDataflowAnalysis<CfFrameState> analysis =
        new CfIntraproceduralDataflowAnalysis<>(
            CfFrameState.bottom(), cfg, new TransferFunction(method));
    DataflowAnalysisResult result = analysis.run(cfg.getEntryBlock());
    // TODO(b/214496607): Determine open interfaces.
  }

  private class TransferFunction
      implements AbstractTransferFunction<CfBlock, CfInstruction, CfFrameState> {

    private final ProgramMethod context;

    TransferFunction(ProgramMethod context) {
      this.context = context;
    }

    @Override
    public TransferFunctionResult<CfFrameState> apply(
        CfInstruction instruction, CfFrameState state) {
      return instruction.evaluate(state, context, appView, appView.dexItemFactory());
    }

    @Override
    public CfFrameState computeBlockEntryState(
        CfBlock cfBlock, CfBlock predecessor, CfFrameState predecessorExitState) {
      // TODO(b/214496607): Implement this.
      throw new Unimplemented();
    }
  }
}
