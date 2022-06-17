// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfAssignability;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfSubtypingAssignability;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClassAndMember;
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
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class CfOpenClosedInterfacesAnalysis {

  private final AppView<AppInfoWithLiveness> appView;
  private final CfAssignability assignability;
  private final InternalOptions options;

  private final ProgramMethodMap<UnverifiableCfCodeDiagnostic> unverifiableCodeDiagnostics =
      ProgramMethodMap.createConcurrent();

  public CfOpenClosedInterfacesAnalysis(AppView<AppInfoWithLiveness> appView) {
    InternalOptions options = appView.options();
    this.appView = appView;
    this.assignability = new CfSubtypingAssignability(appView);
    this.options = options;
  }

  public boolean run(ExecutorService executorService) throws ExecutionException {
    processClasses(executorService);
    reportUnverifiableCodeDiagnostics();
    return true;
  }

  private void processClasses(ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(appView.appInfo().classes(), this::processClass, executorService);
  }

  private void processClass(DexProgramClass clazz) {
    clazz.forEachProgramMethodMatching(DexEncodedMethod::hasCode, this::processMethod);
  }

  private void processMethod(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (!code.isCfCode()) {
      assert code.isDefaultInstanceInitializerCode() || code.isDexCode() || code.isThrowNullCode();
      return;
    }

    CfCode cfCode = code.asCfCode();
    CfControlFlowGraph cfg = CfControlFlowGraph.create(cfCode, options);
    TransferFunction transfer = new TransferFunction(method);
    CfIntraproceduralDataflowAnalysis<CfFrameState> analysis =
        new CfIntraproceduralDataflowAnalysis<>(appView, CfFrameState.bottom(), cfg, transfer);
    DataflowAnalysisResult result = analysis.run(cfg.getEntryBlock());
    assert result.isSuccessfulAnalysisResult();
    for (CfBlock block : cfg.getBlocks()) {
      if (analysis.isIntermediateBlock(block)) {
        continue;
      }
      CfFrameState state = analysis.computeBlockEntryState(block);
      do {
        for (int instructionIndex = block.getFirstInstructionIndex();
            instructionIndex <= block.getLastInstructionIndex();
            instructionIndex++) {
          // TODO(b/214496607): Determine open interfaces.
          CfInstruction instruction = cfCode.getInstruction(instructionIndex);
          state = transfer.apply(instruction, state).asAbstractState();
          if (state.isError()) {
            if (options.getCfCodeAnalysisOptions().isUnverifiableCodeReportingEnabled()) {
              unverifiableCodeDiagnostics.put(
                  method,
                  new UnverifiableCfCodeDiagnostic(
                      method.getMethodReference(),
                      instructionIndex,
                      state.asError().getMessage(),
                      method.getOrigin()));
            }
            return;
          }
        }
        if (analysis.isBlockWithIntermediateSuccessorBlock(block)) {
          block = cfg.getUniqueSuccessor(block);
        } else {
          block = null;
        }
      } while (block != null);
    }
  }

  private void reportUnverifiableCodeDiagnostics() {
    Reporter reporter = appView.reporter();
    List<ProgramMethod> methods = new ArrayList<>(unverifiableCodeDiagnostics.size());
    unverifiableCodeDiagnostics.forEach((method, diagnostic) -> methods.add(method));
    methods.sort(Comparator.comparing(DexClassAndMember::getReference));
    methods.forEach(method -> reporter.warning(unverifiableCodeDiagnostics.get(method)));
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
            public CfAssignability getAssignability() {
              return assignability;
            }

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
      return instruction.evaluate(state, appView, config);
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
