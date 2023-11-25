// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfAssignability.AssignabilityResult;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCodeDiagnostics;
import com.android.tools.r8.graph.CfCodeStackMapValidatingException;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.optimize.interfaces.analysis.ConcreteCfFrameState;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class CfFrameVerifier {

  private final AppView<?> appView;
  private final CfCode code;
  private final CfAnalysisConfig config;
  private final CfFrameVerifierEventConsumer eventConsumer;
  private final DexItemFactory factory;
  private final ProgramMethod method;
  private final Optional<DexMethod> previousMethod;
  private final boolean previousMethodIsInstance;

  private final Deque<CfTryCatch> activeCatchHandlers = new ArrayDeque<>();
  private final Set<CfLabel> tryCatchRangeLabels;

  public CfFrameVerifier(
      AppView<?> appView,
      CfCode code,
      CfAnalysisConfig config,
      CfFrameVerifierEventConsumer eventConsumer,
      ProgramMethod method,
      Optional<DexMethod> previousMethod,
      boolean previousMethodIsInstance) {
    this.appView = appView;
    this.code = code;
    this.config = config;
    this.eventConsumer = eventConsumer;
    this.factory = appView.dexItemFactory();
    this.method = method;
    this.previousMethod = previousMethod;
    this.previousMethodIsInstance = previousMethodIsInstance;
    this.tryCatchRangeLabels = code.getTryCatchRangeLabels();
  }

  public static Builder builder(AppView<?> appView, CfCode code, ProgramMethod method) {
    return new Builder(appView, code, method);
  }

  public StackMapStatus run() {
    if (!appView.options().canUseInputStackMaps()
        || appView.options().testing.disableStackMapVerification) {
      return StackMapStatus.NOT_PRESENT;
    }

    DexEncodedMethod definition = method.getDefinition();
    if (definition.hasClassFileVersion()
        && definition.getClassFileVersion().isLessThan(CfVersion.V1_7)) {
      return StackMapStatus.NOT_PRESENT;
    }

    // Build a map from labels to frames.
    TraversalContinuation<CfCodeDiagnostics, Map<CfLabel, CfFrame>> labelToFrameMapOrError =
        buildLabelToFrameMap();
    if (labelToFrameMapOrError.shouldBreak()) {
      return fail(labelToFrameMapOrError);
    }
    Map<CfLabel, CfFrame> labelToFrameMap = labelToFrameMapOrError.asContinue().getValue();

    // Check try catch ranges.
    CfCodeDiagnostics diagnostics = checkTryCatchRanges(labelToFrameMap);
    if (diagnostics != null) {
      return fail(diagnostics);
    }

    // Compute initial state.
    TraversalContinuation<CfCodeDiagnostics, CfFrameState> initialState = computeInitialState();
    if (initialState.shouldBreak()) {
      return fail(initialState);
    }

    // Linear scan over instructions.
    CfFrameState state = initialState.asContinue().getValue();
    int actualInstructionIndexForReporting = 0;
    for (int i = 0; i < code.getInstructions().size(); i++) {
      CfInstruction instruction = code.getInstruction(i);
      assert !state.isError();
      if (instruction.isLabel()) {
        updateActiveCatchHandlers(instruction.asLabel());
      } else {
        // The ExceptionStackFrame is defined as the current frame having an empty operand stack.
        // All instructions, not only throwing instructions, check the exception frame to be
        // assignable to all exception edges.
        // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.9
        if (appView.options().enableCheckAllInstructionsDuringStackMapVerification
            || instruction.canThrow()) {
          state = checkExceptionEdges(state, labelToFrameMap);
          if (state.isError()) {
            return fail(
                CfCodeStackMapValidatingException.invalidStackMapForInstruction(
                    method,
                    actualInstructionIndexForReporting,
                    instruction,
                    state.asError().getMessage(),
                    appView));
          }
        }
      }
      eventConsumer.acceptInstructionState(instruction, state);
      state = instruction.evaluate(state, appView, config);
      if (instruction.isJumpWithNormalTarget()) {
        CfInstruction fallthroughInstruction =
            (i + 1) < code.getInstructions().size() ? code.getInstruction(i + 1) : null;
        TraversalContinuation<CfCodeDiagnostics, CfFrameState> traversalContinuation =
            instruction.traverseNormalTargets(
                (target, currentState) -> {
                  if (target != fallthroughInstruction) {
                    assert target.isLabel();
                    currentState = checkTarget(currentState, target.asLabel(), labelToFrameMap);
                  }
                  return TraversalContinuation.doContinue(currentState);
                },
                fallthroughInstruction,
                state);
        state = traversalContinuation.asContinue().getValue();
      }
      TraversalContinuation<CfCodeDiagnostics, CfFrameState> traversalContinuation =
          computeStateForNextInstruction(
              instruction, i, actualInstructionIndexForReporting, state, labelToFrameMap);
      if (traversalContinuation.isContinue()) {
        state = traversalContinuation.asContinue().getValue();
      } else {
        return fail(traversalContinuation);
      }
      if (state.isError()) {
        return fail(
            CfCodeStackMapValidatingException.invalidStackMapForInstruction(
                method,
                actualInstructionIndexForReporting,
                instruction,
                state.asError().getMessage(),
                appView));
      }
      if (isActualCfInstruction(instruction)) {
        ++actualInstructionIndexForReporting;
      }
    }
    return StackMapStatus.VALID;
  }

  private static boolean isActualCfInstruction(CfInstruction instruction) {
    return !instruction.isLabel() && !instruction.isFrame() && !instruction.isPosition();
  }

  private TraversalContinuation<CfCodeDiagnostics, Map<CfLabel, CfFrame>> buildLabelToFrameMap() {
    Map<CfLabel, CfFrame> labelToFrameMap = new IdentityHashMap<>();
    List<CfLabel> labels = new ArrayList<>();
    boolean requireStackMapFrame = !code.getTryCatchRanges().isEmpty();
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isFrame()) {
        CfFrame frame = instruction.asFrame();
        if (!labels.isEmpty()) {
          for (CfLabel label : labels) {
            if (labelToFrameMap.containsKey(label)) {
              return TraversalContinuation.doBreak(
                  CfCodeStackMapValidatingException.multipleFramesForLabel(method, appView));
            }
            labelToFrameMap.put(label, frame);
          }
        } else if (instruction != code.getInstruction(0)) {
          // From b/168212806, it is possible that the first instruction is a frame.
          return TraversalContinuation.doBreak(
              CfCodeStackMapValidatingException.unexpectedStackMapFrame(method, appView));
        }
      }
      // We are trying to map a frame to a label, but we can have positions in between, so skip
      // those.
      if (instruction.isPosition()) {
        continue;
      } else if (instruction.isLabel()) {
        labels.add(instruction.asLabel());
      } else {
        labels.clear();
      }
      if (!requireStackMapFrame) {
        requireStackMapFrame = instruction.isJump() && !isFinalAndExitInstruction(instruction);
      }
    }
    // If there are no frames but we have seen a jump instruction, we cannot verify the stack map.
    if (requireStackMapFrame && labelToFrameMap.isEmpty()) {
      return TraversalContinuation.doBreak(
          CfCodeStackMapValidatingException.noFramesForMethodWithJumps(method, appView));
    }
    return TraversalContinuation.doContinue(labelToFrameMap);
  }

  private StackMapStatus fail(TraversalContinuation<CfCodeDiagnostics, ?> traversalContinuation) {
    assert traversalContinuation.shouldBreak();
    return fail(traversalContinuation.asBreak().getValue());
  }

  private StackMapStatus fail(CfCodeDiagnostics diagnostics) {
    eventConsumer.acceptError(diagnostics);
    return StackMapStatus.INVALID;
  }

  private void updateActiveCatchHandlers(CfLabel label) {
    if (tryCatchRangeLabels.contains(label)) {
      for (CfTryCatch tryCatchRange : code.getTryCatchRanges()) {
        if (tryCatchRange.start == label) {
          activeCatchHandlers.add(tryCatchRange);
        }
      }
      activeCatchHandlers.removeIf(currentRange -> currentRange.end == label);
    }
  }

  private CfCodeDiagnostics checkTryCatchRanges(Map<CfLabel, CfFrame> labelToFrameMap) {
    for (CfTryCatch tryCatchRange : code.getTryCatchRanges()) {
      CfCodeDiagnostics diagnostics = checkTryCatchRange(tryCatchRange, labelToFrameMap);
      if (diagnostics != null) {
        return diagnostics;
      }
    }
    return null;
  }

  private CfCodeDiagnostics checkTryCatchRange(
      CfTryCatch tryCatchRange, Map<CfLabel, CfFrame> labelToFrameMap) {
    // According to the spec:
    // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1
    // saying ` and the handler's target (the initial instruction of the handler code) is type
    // safe assuming an incoming type state T. The type state T is derived from ExcStackFrame
    // by replacing the operand stack with a stack whose sole element is the handler's
    // exception class.
    for (CfLabel target : tryCatchRange.getTargets()) {
      CfFrame destinationFrame = labelToFrameMap.get(target);
      if (destinationFrame == null) {
        return CfCodeStackMapValidatingException.invalidTryCatchRange(
            method, tryCatchRange, "No frame for target catch range target", appView);
      }
      // From the spec: the handler's exception class is assignable to the class Throwable.
      for (DexType guard : tryCatchRange.guards) {
        if (!config.getAssignability().isAssignable(guard, factory.throwableType)) {
          return CfCodeStackMapValidatingException.invalidTryCatchRange(
              method,
              tryCatchRange,
              "Could not assign " + guard.getTypeName() + " to java.lang.Throwable",
              appView);
        }
        Deque<PreciseFrameType> sourceStack =
            ImmutableDeque.of(FrameType.initializedNonNullReference(guard));
        AssignabilityResult assignabilityResult =
            config.getAssignability().isStackAssignable(sourceStack, destinationFrame.getStack());
        if (assignabilityResult.isFailed()) {
          return CfCodeStackMapValidatingException.invalidTryCatchRange(
              method, tryCatchRange, assignabilityResult.asFailed().getMessage(), appView);
        }
      }
    }
    return null;
  }

  private CfFrameState checkExceptionEdges(
      CfFrameState state, Map<CfLabel, CfFrame> labelToFrameMap) {
    for (CfTryCatch currentCatchRange : activeCatchHandlers) {
      for (CfLabel target : currentCatchRange.getTargets()) {
        CfFrame destinationFrame = labelToFrameMap.get(target);
        if (destinationFrame == null) {
          return CfFrameState.error("No frame for target catch range target");
        }
        state = state.checkLocals(config, destinationFrame);
      }
    }
    return state;
  }

  private CfFrameState checkTarget(
      CfFrameState state, CfLabel label, Map<CfLabel, CfFrame> labelToFrameMap) {
    CfFrame destinationFrame = labelToFrameMap.get(label);
    return destinationFrame != null
        ? state.checkLocals(config, destinationFrame).checkStack(config, destinationFrame)
        : CfFrameState.error("No destination frame");
  }

  @SuppressWarnings("UnusedVariable")
  private TraversalContinuation<CfCodeDiagnostics, CfFrameState> computeInitialState() {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    CfFrameState state = new ConcreteCfFrameState();
    int localIndex = 0;
    DexMethod context = previousMethod.orElse(method.getReference());
    if (method.getDefinition().isInstance() || previousMethodIsInstance) {
      state =
          state.storeLocal(
              localIndex,
              context.isInstanceInitializerInlineIntoOrMerged(appView)
                  ? FrameType.uninitializedThis()
                  : FrameType.initializedNonNullReference(context.getHolderType()),
              config);
      localIndex++;
    }
    for (DexType parameter : context.getParameters()) {
      state = state.storeLocal(localIndex, FrameType.initialized(parameter), config);
      localIndex += parameter.getRequiredRegisters();
    }
    if (state.isError()) {
      return TraversalContinuation.doBreak(
          CfCodeStackMapValidatingException.invalidStackMapForInstruction(
              method, 0, code.getInstruction(0), state.asError().getMessage(), appView));
    }
    return TraversalContinuation.doContinue(state);
  }

  private TraversalContinuation<CfCodeDiagnostics, CfFrameState> computeStateForNextInstruction(
      CfInstruction instruction,
      int instructionIndex,
      int actualInstructionIndexForReporting,
      CfFrameState state,
      Map<CfLabel, CfFrame> labelToFrameMap) {
    if (!instruction.isJump()) {
      return TraversalContinuation.doContinue(state);
    }
    if (instructionIndex == code.getInstructions().size() - 1) {
      return TraversalContinuation.doContinue(CfFrameState.bottom());
    }
    if (instructionIndex == code.getInstructions().size() - 2
        && code.getInstruction(instructionIndex + 1).isLabel()) {
      return TraversalContinuation.doContinue(CfFrameState.bottom());
    }
    if (instruction.asJump().hasFallthrough()) {
      return TraversalContinuation.doContinue(state);
    }
    CfInstruction nextInstruction = code.getInstruction(instructionIndex + 1);
    CfFrame nextFrame = null;
    if (nextInstruction.isFrame()) {
      nextFrame = nextInstruction.asFrame();
    } else if (nextInstruction.isLabel()) {
      nextFrame = labelToFrameMap.get(nextInstruction.asLabel());
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
            method,
            actualInstructionIndexForReporting + 1,
            nextInstruction,
            "Expected frame instruction",
            appView));
  }

  private boolean isFinalAndExitInstruction(CfInstruction instruction) {
    boolean isReturnOrThrow = instruction.isThrow() || instruction.isReturn();
    if (!isReturnOrThrow) {
      return false;
    }
    for (int i = code.getInstructions().size() - 1; i >= 0; i--) {
      CfInstruction instr = code.getInstruction(i);
      if (instr == instruction) {
        return true;
      }
      if (instr.isPosition() || instr.isLabel()) {
        continue;
      }
      return false;
    }
    throw new Unreachable("Instruction " + instruction + " should be in instructions");
  }

  public enum StackMapStatus {
    NOT_VERIFIED,
    NOT_PRESENT,
    INVALID,
    VALID;

    public boolean isNotPresent() {
      return this == NOT_PRESENT;
    }

    public boolean isNotVerified() {
      return this == NOT_VERIFIED;
    }

    public boolean isValid() {
      return this == VALID;
    }

    public boolean isValidOrNotPresent() {
      return this == VALID || this == NOT_PRESENT;
    }

    public boolean isInvalidOrNotPresent() {
      return this == INVALID || this == NOT_PRESENT;
    }
  }

  public static class Builder {

    private final AppView<?> appView;
    private final CfCode code;
    private final ProgramMethod method;

    private CfAnalysisConfig config;
    private CfFrameVerifierEventConsumer eventConsumer;
    private Optional<DexMethod> previousMethod = Optional.empty();
    private boolean previousMethodIsInstance;

    Builder(AppView<?> appView, CfCode code, ProgramMethod method) {
      this.appView = appView;
      this.code = code;
      this.method = method;
    }

    public Builder setCodeLens(GraphLens codeLens) {
      if (codeLens != appView.graphLens()) {
        this.previousMethod =
            Optional.of(
                appView.graphLens().getOriginalMethodSignature(method.getReference(), codeLens));
        this.previousMethodIsInstance =
            method.getDefinition().isInstance()
                || appView
                    .graphLens()
                    .lookupPrototypeChangesForMethodDefinition(method.getReference(), codeLens)
                    .getArgumentInfoCollection()
                    .isConvertedToStaticMethod();
      }
      return this;
    }

    public Builder setConfig(CfAnalysisConfig config) {
      this.config = config;
      return this;
    }

    public Builder setEventConsumer(CfFrameVerifierEventConsumer eventConsumer) {
      this.eventConsumer = eventConsumer;
      return this;
    }

    public CfFrameVerifier build() {
      assert eventConsumer != null;
      return new CfFrameVerifier(
          appView,
          code,
          buildConfig(),
          eventConsumer,
          method,
          previousMethod,
          previousMethodIsInstance);
    }

    private CfAnalysisConfig buildConfig() {
      return config != null
          ? config
          : new CfFrameVerifierDefaultAnalysisConfig(appView, code, method, previousMethod);
    }
  }
}
