// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
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
    CfControlFlowGraph cfg = CfControlFlowGraph.create(cfCode, appView.options());
    CfIntraproceduralDataflowAnalysis<CfFrameState> analysis =
        new CfIntraproceduralDataflowAnalysis<>(
            appView, CfFrameState.bottom(), cfg, new TransferFunction(method));
    DataflowAnalysisResult result = analysis.run(cfg.getEntryBlock());
    // TODO(b/214496607): Determine open interfaces.
  }

  private class TransferFunction
      implements AbstractTransferFunction<CfBlock, CfInstruction, CfFrameState> {

    private final CfCode code;
    private final CfAnalysisConfig config;
    private final ProgramMethod context;

    TransferFunction(ProgramMethod context) {
      CfCode code = context.getDefinition().getCode().asCfCode();
      int maxLocals = code.getMaxLocals();
      int maxStack = code.getMaxStack();
      this.code = code;
      this.config =
          new CfAnalysisConfig() {

            @Override
            public DexMethod getCurrentContext() {
              return context.getReference();
            }

            @Override
            public int getMaxLocals() {
              return maxLocals;
            }

            @Override
            public int getMaxStack() {
              return maxStack;
            }

            @Override
            public boolean isImmediateSuperClassOfCurrentContext(DexType type) {
              return type == context.getHolder().getSuperType();
            }
          };
      this.context = context;
    }

    @Override
    public TransferFunctionResult<CfFrameState> apply(
        CfInstruction instruction, CfFrameState state) {
      return instruction.evaluate(state, appView, config, appView.dexItemFactory());
    }

    @Override
    public CfFrameState computeInitialState(CfBlock entryBlock, CfFrameState bottom) {
      CfFrameState initialState = new ConcreteCfFrameState();
      int localIndex = 0;
      if (context.getDefinition().isInstance()) {
        if (context.getDefinition().isInstanceInitializer()) {
          initialState = initialState.storeLocal(localIndex, FrameType.uninitializedThis(), config);
        } else {
          initialState =
              initialState.storeLocal(
                  localIndex, FrameType.initialized(context.getHolderType()), config);
        }
        localIndex++;
      }
      for (DexType parameter : context.getParameters()) {
        initialState =
            initialState.storeLocal(localIndex, FrameType.initialized(parameter), config);
        localIndex += parameter.getRequiredRegisters();
      }
      return initialState;
    }

    @Override
    public CfFrameState computeBlockEntryState(
        CfBlock block, CfBlock predecessor, CfFrameState predecessorExitState) {
      return predecessorExitState;
    }

    @Override
    public boolean shouldTransferExceptionalControlFlowFromInstruction(
        CfBlock throwBlock, CfInstruction throwInstruction) {
      // All locals defined after the first throwing instruction cannot be accessed in the catch
      // handler. Therefore, we only propagate the state from the first throwing instruction to the
      // catch handler.
      assert throwBlock.hasThrowingInstruction();
      return throwInstruction == code.getInstruction(throwBlock.getFirstThrowingInstructionIndex());
    }

    @Override
    public CfFrameState computeExceptionalBlockEntryState(
        CfBlock block,
        DexType guard,
        CfBlock throwBlock,
        CfInstruction throwInstruction,
        CfFrameState throwState) {
      return throwState.pushException(config, guard);
    }
  }
}
