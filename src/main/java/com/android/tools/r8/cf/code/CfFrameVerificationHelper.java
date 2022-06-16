// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.code.CfAssignability.AssignabilityResult;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCodeDiagnostics;
import com.android.tools.r8.graph.CfCodeStackMapValidatingException;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.optimize.interfaces.analysis.ConcreteCfFrameState;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CfFrameVerificationHelper implements CfAnalysisConfig {

  private final AppView<?> appView;
  private final CfAssignability assignability;
  private final CfCode code;
  private final GraphLens codeLens;
  private final DexItemFactory factory;
  private final ProgramMethod method;
  private final DexMethod previousMethod;

  private final Map<CfLabel, CfFrame> stateMap;
  private final List<CfTryCatch> tryCatchRanges;

  private final Deque<CfTryCatch> currentCatchRanges = new ArrayDeque<>();
  private final Set<CfLabel> tryCatchRangeLabels;

  public CfFrameVerificationHelper(
      AppView<?> appView,
      CfCode code,
      GraphLens codeLens,
      ProgramMethod method,
      Map<CfLabel, CfFrame> stateMap,
      List<CfTryCatch> tryCatchRanges) {
    this.appView = appView;
    this.assignability = new CfAssignability(appView);
    this.code = code;
    this.codeLens = codeLens;
    this.method = method;
    this.previousMethod =
        appView.graphLens().getOriginalMethodSignature(method.getReference(), codeLens);
    this.stateMap = stateMap;
    this.tryCatchRanges = tryCatchRanges;
    this.factory = appView.dexItemFactory();
    // Compute all labels that marks a start or end to catch ranges.
    tryCatchRangeLabels = Sets.newIdentityHashSet();
    for (CfTryCatch tryCatchRange : tryCatchRanges) {
      tryCatchRangeLabels.add(tryCatchRange.start);
      tryCatchRangeLabels.add(tryCatchRange.end);
    }
  }

  @Override
  public CfAssignability getAssignability() {
    return assignability;
  }

  @Override
  public DexMethod getCurrentContext() {
    return previousMethod;
  }

  @Override
  public int getMaxLocals() {
    return code.getMaxLocals();
  }

  @Override
  public int getMaxStack() {
    return code.getMaxStack();
  }

  @Override
  public boolean isImmediateSuperClassOfCurrentContext(DexType type) {
    // If the code is rewritten according to the graph lens, we perform a strict check that the
    // given type is the same as the current holder's super class.
    if (codeLens == appView.graphLens()) {
      return type == method.getHolder().getSuperType();
    }
    // Otherwise, we don't know what the super class of the current class was at the point of the
    // code lens. We return true, which has the consequence that we may accept a constructor call
    // for an uninitialized-this value where the constructor is not defined in the immediate parent
    // class.
    return true;
  }

  public void seenLabel(CfLabel label) {
    if (tryCatchRangeLabels.contains(label)) {
      for (CfTryCatch tryCatchRange : tryCatchRanges) {
        if (tryCatchRange.start == label) {
          currentCatchRanges.add(tryCatchRange);
        }
      }
      currentCatchRanges.removeIf(currentRange -> currentRange.end == label);
    }
  }

  public CfCodeDiagnostics checkTryCatchRanges() {
    for (CfTryCatch tryCatchRange : tryCatchRanges) {
      CfCodeDiagnostics diagnostics = checkTryCatchRange(tryCatchRange);
      if (diagnostics != null) {
        return diagnostics;
      }
    }
    return null;
  }

  public CfCodeDiagnostics checkTryCatchRange(CfTryCatch tryCatchRange) {
    // According to the spec:
    // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1
    // saying ` and the handler's target (the initial instruction of the handler code) is type
    // safe assuming an incoming type state T. The type state T is derived from ExcStackFrame
    // by replacing the operand stack with a stack whose sole element is the handler's
    // exception class.
    for (CfLabel target : tryCatchRange.getTargets()) {
      CfFrame destinationFrame = stateMap.get(target);
      if (destinationFrame == null) {
        return CfCodeStackMapValidatingException.invalidTryCatchRange(
            method, tryCatchRange, "No frame for target catch range target", appView);
      }
      // From the spec: the handler's exception class is assignable to the class Throwable.
      for (DexType guard : tryCatchRange.guards) {
        if (!assignability.isAssignable(guard, factory.throwableType)) {
          return CfCodeStackMapValidatingException.invalidTryCatchRange(
              method,
              tryCatchRange,
              "Could not assign " + guard.getTypeName() + " to java.lang.Throwable",
              appView);
        }
        Deque<PreciseFrameType> sourceStack = ImmutableDeque.of(FrameType.initialized(guard));
        AssignabilityResult assignabilityResult =
            assignability.isStackAssignable(sourceStack, destinationFrame.getStack());
        if (assignabilityResult.isFailed()) {
          return CfCodeStackMapValidatingException.invalidTryCatchRange(
              method, tryCatchRange, assignabilityResult.asFailed().getMessage(), appView);
        }
      }
    }
    return null;
  }

  public CfFrameState checkExceptionEdges(CfFrameState state) {
    for (CfTryCatch currentCatchRange : currentCatchRanges) {
      for (CfLabel target : currentCatchRange.getTargets()) {
        CfFrame destinationFrame = stateMap.get(target);
        if (destinationFrame == null) {
          return CfFrameState.error("No frame for target catch range target");
        }
        state = state.checkLocals(this, destinationFrame);
      }
    }
    return state;
  }

  public CfFrameState checkTarget(CfFrameState state, CfLabel label) {
    CfFrame destinationFrame = getDestinationFrame(label);
    return destinationFrame != null
        ? state.checkLocals(this, destinationFrame).checkStack(this, destinationFrame)
        : CfFrameState.error("No destination frame");
  }

  private CfFrame getDestinationFrame(CfLabel label) {
    return stateMap.get(label);
  }

  public TraversalContinuation<CfCodeDiagnostics, CfFrameState> computeStateForNextInstruction(
      CfInstruction instruction, int instructionIndex, CfFrameState state) {
    if (!instruction.isJump()) {
      return TraversalContinuation.doContinue(state);
    }
    if (instructionIndex == code.getInstructions().size() - 1) {
      return TraversalContinuation.doContinue(CfFrameState.bottom());
    }
    if (instructionIndex == code.getInstructions().size() - 2
        && code.getInstructions().get(instructionIndex + 1).isLabel()) {
      return TraversalContinuation.doContinue(CfFrameState.bottom());
    }
    if (instruction.asJump().hasFallthrough()) {
      return TraversalContinuation.doContinue(state);
    }
    int nextInstructionIndex = instructionIndex + 1;
    CfInstruction nextInstruction = code.getInstructions().get(nextInstructionIndex);
    CfFrame nextFrame = null;
    if (nextInstruction.isFrame()) {
      nextFrame = nextInstruction.asFrame();
    } else if (nextInstruction.isLabel()) {
      nextFrame = getDestinationFrame(nextInstruction.asLabel());
    }
    if (nextFrame != null) {
      CfFrame currentFrameCopy = nextFrame.mutableCopy();
      return TraversalContinuation.doContinue(
          new ConcreteCfFrameState(
              currentFrameCopy.getMutableLocals(),
              currentFrameCopy.getMutableStack(),
              currentFrameCopy.computeStackSize()));
    }
    return TraversalContinuation.doBreak(
        CfCodeStackMapValidatingException.invalidStackMapForInstruction(
            method, nextInstructionIndex, nextInstruction, "Expected frame instruction", appView));
  }
}
