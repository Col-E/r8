// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMaps.emptyMap;

import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrameVerifier;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfSwitch;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.errors.InvalidDebugInfoException;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
import com.android.tools.r8.graph.CfCodeDiagnostics;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DebugLocalInfo.PrintLevel;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.CanonicalPositions;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.Monitor;
import com.android.tools.r8.ir.code.MonitorType;
import com.android.tools.r8.ir.code.Phi.RegisterReadType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.code.ValueTypeConstraint;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.CfState.Snapshot;
import com.android.tools.r8.ir.conversion.IRBuilder.BlockInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOutputMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CfSourceCode implements SourceCode {

  private BlockInfo currentBlockInfo;
  private boolean hasExitingInstruction = false;

  private static final int EXCEPTIONAL_SYNC_EXIT_OFFSET = -2;
  private final boolean needsGeneratedMethodSynchronization;
  private boolean currentlyGeneratingMethodSynchronization = false;
  private Monitor monitorEnter = null;

  private static class TryHandlerList {

    public final int startOffset;
    public final int endOffset;
    public final List<DexType> guards;
    public final IntList offsets;

    TryHandlerList(int startOffset, int endOffset, List<DexType> guards, IntList offsets) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.guards = guards;
      this.offsets = offsets;
    }

    boolean validFor(int instructionOffset) {
      return startOffset <= instructionOffset && instructionOffset < endOffset;
    }

    boolean isEmpty() {
      assert guards.isEmpty() == offsets.isEmpty();
      return guards.isEmpty();
    }

    @SuppressWarnings("ReferenceEquality")
    static TryHandlerList computeTryHandlers(
        int instructionOffset,
        List<CfTryCatch> tryCatchRanges,
        Reference2IntMap<CfLabel> labelOffsets,
        boolean needsGeneratedMethodSynchronization,
        DexItemFactory factory) {
      int startOffset = 0;
      int endOffset = Integer.MAX_VALUE;
      List<DexType> guards = new ArrayList<>();
      IntList offsets = new IntArrayList();
      ReferenceSet<DexType> seen = new ReferenceOpenHashSet<>();
      boolean seenCatchAll = false;
      for (CfTryCatch tryCatch : tryCatchRanges) {
        int start = labelOffsets.getInt(tryCatch.start);
        int end = labelOffsets.getInt(tryCatch.end);
        if (start > instructionOffset) {
          endOffset = Math.min(endOffset, start);
          continue;
        } else if (instructionOffset >= end) {
          startOffset = Math.max(startOffset, end);
          continue;
        }
        startOffset = Math.max(startOffset, start);
        endOffset = Math.min(endOffset, end);
        for (int i = 0; i < tryCatch.guards.size() && !seenCatchAll; i++) {
          DexType guard = tryCatch.guards.get(i);
          if (seen.add(guard)) {
            guards.add(guard);
            offsets.add(labelOffsets.getInt(tryCatch.targets.get(i)));
            seenCatchAll = guard == factory.throwableType;
          }
        }
        if (seenCatchAll) {
          break;
        }
      }
      if (needsGeneratedMethodSynchronization && !seenCatchAll) {
        guards.add(factory.throwableType);
        offsets.add(EXCEPTIONAL_SYNC_EXIT_OFFSET);
      }
      return new TryHandlerList(startOffset, endOffset, guards, offsets);
    }
  }

  private static class LocalVariableList {

    public static final LocalVariableList EMPTY = new LocalVariableList(0, 0, emptyMap());
    public final int startOffset;
    public final int endOffset;
    public final Int2ReferenceMap<DebugLocalInfo> locals;

    private LocalVariableList(
        int startOffset, int endOffset, Int2ReferenceMap<DebugLocalInfo> locals) {
      this.startOffset = startOffset;
      this.endOffset = endOffset;
      this.locals = locals;
    }

    static LocalVariableList compute(
        int instructionOffset,
        List<CfCode.LocalVariableInfo> locals,
        Reference2IntMap<CfLabel> labelOffsets) {
      int startOffset = Integer.MIN_VALUE;
      int endOffset = Integer.MAX_VALUE;
      Int2ReferenceMap<DebugLocalInfo> currentLocals = null;
      for (LocalVariableInfo local : locals) {
        int start = labelOffsets.getInt(local.getStart());
        int end = labelOffsets.getInt(local.getEnd());
        if (start > instructionOffset) {
          endOffset = Math.min(endOffset, start);
          continue;
        } else if (instructionOffset >= end) {
          startOffset = Math.max(startOffset, end);
          continue;
        }
        if (currentLocals == null) {
          currentLocals = new Int2ReferenceOpenHashMap<>();
        }
        startOffset = Math.max(startOffset, start);
        endOffset = Math.min(endOffset, end);
        currentLocals.put(local.getIndex(), local.getLocal());
      }
      return new LocalVariableList(
          startOffset, endOffset, currentLocals == null ? emptyMap() : currentLocals);
    }

    boolean validFor(int instructionOffset) {
      return startOffset <= instructionOffset && instructionOffset < endOffset;
    }

  }

  private CfState state;
  private final List<CfCode.LocalVariableInfo> localVariables;
  private final CfCode code;
  private final ProgramMethod method;
  private final Origin origin;
  private final AppView<?> appView;

  private final Reference2IntMap<CfLabel> labelOffsets = new Reference2IntOpenHashMap<>();
  private TryHandlerList cachedTryHandlerList;
  private LocalVariableList cachedLocalVariableList;
  private int currentInstructionIndex;
  private int currentBlockIndex;
  private boolean inPrelude;
  private Int2ReferenceMap<DebugLocalInfo> incomingLocals;
  private Int2ReferenceMap<DebugLocalInfo> outgoingLocals;
  private Int2ReferenceMap<CfState.Snapshot> incomingState = new Int2ReferenceOpenHashMap<>();
  private final CanonicalPositions canonicalPositions;
  private final InternalOutputMode internalOutputMode;

  public CfSourceCode(
      CfCode code,
      List<CfCode.LocalVariableInfo> localVariables,
      ProgramMethod method,
      DexMethod originalMethod,
      Position callerPosition,
      Origin origin,
      AppView<?> appView) {
    this.code = code;
    this.localVariables = localVariables;
    this.method = method;
    this.origin = origin;
    this.appView = appView;
    int cfPositionCount = 0;
    for (int i = 0; i < code.getInstructions().size(); i++) {
      CfInstruction instruction = code.getInstructions().get(i);
      if (instruction instanceof CfLabel) {
        labelOffsets.put((CfLabel) instruction, instructionOffset(i));
      }
      if (instruction instanceof CfPosition) {
        ++cfPositionCount;
      }
    }
    this.state = new CfState(origin);
    canonicalPositions =
        new CanonicalPositions(
            callerPosition,
            cfPositionCount,
            originalMethod,
            method.getDefinition().isD8R8Synthesized(),
            code.getPreamblePosition());
    internalOutputMode = appView.options().getInternalOutputMode();

    needsGeneratedMethodSynchronization =
        !getMethod().isProcessed()
            && internalOutputMode.isGeneratingDex()
            && getMethod().isSynchronized();
  }

  private DexEncodedMethod getMethod() {
    return method.getDefinition();
  }

  public Origin getOrigin() {
    return origin;
  }

  public DexType getOriginalHolder() {
    return code.getOriginalHolder();
  }

  @Override
  public int instructionCount() {
    return code.getInstructions().size();
  }

  @Override
  public int instructionIndex(int instructionOffset) {
    return instructionOffset;
  }

  @Override
  public int instructionOffset(int instructionIndex) {
    return instructionIndex;
  }

  @Override
  public boolean verifyRegister(int register) {
    return true;
  }

  @Override
  public void setUp() {}

  @Override
  public void clear() {}

  @Override
  public int traceInstruction(int instructionIndex, IRBuilder builder) {
    CfInstruction instruction = code.getInstructions().get(instructionIndex);
    AppView<?> appView = builder.appView;
    assert appView.options().isGeneratingClassFiles()
        == internalOutputMode.isGeneratingClassFiles();
    if (instruction.canThrow()) {
      TryHandlerList tryHandlers = getTryHandlers(instructionIndex, appView.dexItemFactory());
      if (!tryHandlers.isEmpty()) {
        // Ensure the block starts at the start of the try-range (don't enqueue, not a target).
        builder.ensureBlockWithoutEnqueuing(tryHandlers.startOffset);
        IntSet seen = new IntOpenHashSet();
        for (int offset : tryHandlers.offsets) {
          if (seen.add(offset)) {
            builder.ensureExceptionalSuccessorBlock(instructionIndex, offset);
          }
        }
        if (!(instruction instanceof CfThrow)) {
          builder.ensureNormalSuccessorBlock(instructionIndex, instructionIndex + 1);
        }
        return instructionIndex;
      }
      // If the throwable instruction is "throw" it closes the block.
      hasExitingInstruction |= instruction instanceof CfThrow;
      return (instruction instanceof CfThrow) ? instructionIndex : -1;
    }
    if (isControlFlow(instruction)) {
      for (int target : getTargets(instructionIndex)) {
        builder.ensureNormalSuccessorBlock(instructionIndex, target);
      }
      hasExitingInstruction |= instruction.isReturn();
      return instructionIndex;
    }
    return -1;
  }

  private TryHandlerList getTryHandlers(int instructionOffset, DexItemFactory factory) {
    if (cachedTryHandlerList == null || !cachedTryHandlerList.validFor(instructionOffset)) {
      cachedTryHandlerList =
          TryHandlerList.computeTryHandlers(
              instructionOffset,
              code.getTryCatchRanges(),
              labelOffsets,
              needsGeneratedMethodSynchronization,
              factory);
    }
    return cachedTryHandlerList;
  }

  private LocalVariableList getLocalVariables(int instructionOffset) {
    if (cachedLocalVariableList == null || !cachedLocalVariableList.validFor(instructionOffset)) {
      cachedLocalVariableList =
          LocalVariableList.compute(instructionOffset, localVariables, labelOffsets);
    }
    return cachedLocalVariableList;
  }

  private int[] getTargets(int instructionIndex) {
    CfInstruction instruction = code.getInstructions().get(instructionIndex);
    assert isControlFlow(instruction);
    CfLabel target = instruction.getTarget();
    if (instruction.isReturn() || instruction instanceof CfThrow) {
      assert target == null;
      return new int[] {};
    }
    assert instruction instanceof CfSwitch || target != null
        : "getTargets(): Non-control flow instruction " + instruction.getClass();
    if (instruction instanceof CfSwitch) {
      CfSwitch cfSwitch = (CfSwitch) instruction;
      List<CfLabel> targets = cfSwitch.getSwitchTargets();
      int[] res = new int[targets.size() + 1];
      for (int i = 0; i < targets.size(); i++) {
        res[i] = labelOffsets.getInt(targets.get(i));
      }
      res[targets.size()] = labelOffsets.getInt(cfSwitch.getDefaultTarget());
      return res;
    }
    int targetIndex = labelOffsets.getInt(target);
    if (instruction instanceof CfGoto) {
      return new int[] {targetIndex};
    }
    assert instruction.isConditionalJump();
    return new int[] {targetIndex, instructionIndex + 1};
  }

  @Override
  public void buildPrelude(IRBuilder builder) {
    assert !inPrelude;
    inPrelude = true;
    state.buildPrelude(canonicalPositions.getPreamblePosition());
    setLocalVariableLists();
    builder.buildArgumentsWithRewrittenPrototypeChanges(0, getMethod(), state::write);
    // Add debug information for all locals at the initial label.
    Int2ReferenceMap<DebugLocalInfo> locals = getLocalVariables(0).locals;
    if (!locals.isEmpty()) {
      int firstLocalIndex = 0;
      if (!getMethod().isStatic()) {
        firstLocalIndex++;
      }
      for (DexType value : getMethod().getProto().parameters.values) {
        firstLocalIndex++;
        if (value.isLongType() || value.isDoubleType()) {
          firstLocalIndex++;
        }
      }
      for (Entry<DebugLocalInfo> entry : locals.int2ReferenceEntrySet()) {
        if (firstLocalIndex <= entry.getIntKey()) {
          builder.addDebugLocalStart(entry.getIntKey(), entry.getValue());
        }
      }
    }
    if (needsGeneratedMethodSynchronization) {
      buildMethodEnterSynchronization(builder);
    }
    Position entryPosition = getCanonicalDebugPositionAtOffset(0);
    if (!state.getPosition().equals(entryPosition)) {
      state.setPosition(entryPosition);
      builder.addDebugPosition(state.getPosition());
    }
    recordStateForTarget(0, state.getSnapshot());
    inPrelude = false;
  }

  private boolean isCurrentlyGeneratingMethodSynchronization() {
    return currentlyGeneratingMethodSynchronization;
  }

  private boolean isExceptionalExitForMethodSynchronization(int instructionIndex) {
    return instructionIndex == EXCEPTIONAL_SYNC_EXIT_OFFSET;
  }

  private void buildMethodEnterSynchronization(IRBuilder builder) {
    assert needsGeneratedMethodSynchronization;
    currentlyGeneratingMethodSynchronization = true;
    DexType type = method.getHolderType();
    int monitorRegister;
    if (getMethod().isStatic()) {
      monitorRegister = state.push(type).register;
      state.pop();
      builder.addConstClass(monitorRegister, type);
    } else {
      monitorRegister = state.read(0).register;
    }
    // Build the monitor enter and save it for when generating exits later.
    monitorEnter = builder.addMonitor(MonitorType.ENTER, monitorRegister);
    currentlyGeneratingMethodSynchronization = false;
  }

  private void buildExceptionalExitMethodSynchronization(IRBuilder builder) {
    assert needsGeneratedMethodSynchronization;
    currentlyGeneratingMethodSynchronization = true;
    state.setPosition(getCanonicalDebugPositionAtOffset(EXCEPTIONAL_SYNC_EXIT_OFFSET));
    builder.add(new Monitor(MonitorType.EXIT, monitorEnter.inValues().get(0)));
    builder.addThrow(getMoveExceptionRegister(0));
    currentlyGeneratingMethodSynchronization = false;
  }

  @Override
  public void buildPostlude(IRBuilder builder) {
    if (needsGeneratedMethodSynchronization) {
      currentlyGeneratingMethodSynchronization = true;
      builder.add(new Monitor(MonitorType.EXIT, monitorEnter.inValues().get(0)));
      currentlyGeneratingMethodSynchronization = false;
    }
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void buildBlockTransfer(
      IRBuilder builder, int predecessorOffset, int successorOffset, boolean isExceptional) {
    if (predecessorOffset == IRBuilder.INITIAL_BLOCK_OFFSET
        || isExceptionalExitForMethodSynchronization(successorOffset)) {
      return;
    }
    // The transfer has not yet taken place, so the current position is that of the predecessor,
    // except for exceptional edges where the transfer has already taken place.
    state.setPosition(
        getCanonicalDebugPositionAtOffset(isExceptional ? successorOffset : predecessorOffset));

    // Manually compute the local variable change for the block transfer.
    Int2ReferenceMap<DebugLocalInfo> atSource = getLocalVariables(predecessorOffset).locals;
    Int2ReferenceMap<DebugLocalInfo> atTarget = getLocalVariables(successorOffset).locals;
    if (!isExceptional) {
      for (Entry<DebugLocalInfo> entry : atSource.int2ReferenceEntrySet()) {
        if (atTarget.get(entry.getIntKey()) != entry.getValue()) {
          builder.addDebugLocalEnd(entry.getIntKey(), entry.getValue());
        }
      }
    }
    for (Entry<DebugLocalInfo> entry : atTarget.int2ReferenceEntrySet()) {
      if (atSource.get(entry.getIntKey()) != entry.getValue()) {
        builder.addDebugLocalStart(entry.getIntKey(), entry.getValue());
      }
    }

    // If there are no explicit exits from the method (ie, this method is a loop without an explict
    // return or an unhandled throw) then we cannot guarentee that a local live in a successor will
    // ensure it is marked as such (via an explict 'end' marker) and thus be live in predecessors.
    // In this case we insert an 'end' point on all explicit goto instructions ensuring that any
    // back-edge will explicitly keep locals live at that point.
    if (!hasExitingInstruction && code.getInstructions().get(predecessorOffset) instanceof CfGoto) {
      assert !isExceptional;
      for (Entry<DebugLocalInfo> entry : atSource.int2ReferenceEntrySet()) {
        if (atTarget.get(entry.getIntKey()) == entry.getValue()) {
          builder.addDebugLocalEnd(entry.getIntKey(), entry.getValue());
        }
      }
    }
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void buildInstruction(
      IRBuilder builder, int instructionIndex, boolean firstBlockInstruction) {
    if (isExceptionalExitForMethodSynchronization(instructionIndex)) {
      buildExceptionalExitMethodSynchronization(builder);
      return;
    }
    CfInstruction instruction = code.getInstructions().get(instructionIndex);
    currentInstructionIndex = instructionIndex;
    if (firstBlockInstruction) {
      currentBlockInfo = builder.getCFG().get(instructionIndex);
      if (instructionIndex == 0 && currentBlockInfo == null) {
        // If the entry block is also a target the actual entry block is at offset -1.
        currentBlockInfo = builder.getCFG().get(IRBuilder.INITIAL_BLOCK_OFFSET);
      }
      state.reset(
          incomingState.get(instructionIndex),
          instructionIndex == 0,
          getCanonicalDebugPositionAtOffset(instructionIndex));
      currentBlockIndex = currentInstructionIndex;
    }

    assert currentBlockInfo != null;
    setLocalVariableLists();

    if (instruction.canThrow()) {
      Snapshot exceptionTransfer =
          state.getSnapshot().exceptionTransfer(builder.appView.dexItemFactory().throwableType);
      for (int target : currentBlockInfo.exceptionalSuccessors) {
        recordStateForTarget(target, exceptionTransfer);
      }
    }

    boolean localsChanged = localsChanged();
    boolean hasNextInstructionInCurrentBlock =
        instructionIndex + 1 != instructionCount()
            && !builder.getCFG().containsKey(instructionIndex + 1);

    if (instruction.isReturn() || instruction instanceof CfThrow) {
      // Ensure that all live locals are marked as ending on method exits.
      assert currentBlockInfo.normalSuccessors.isEmpty();
      if (currentBlockInfo.exceptionalSuccessors.isEmpty()) {
        incomingLocals.forEach(builder::addDebugLocalEnd);
      } else if (!incomingLocals.isEmpty()) {
        // If the throw instruction does not exit the method, we must end (ensure liveness of) all
        // locals that are not kept live by at least one of the exceptional successors.
        Int2ReferenceMap<DebugLocalInfo> live = new Int2ReferenceOpenHashMap<>();
        for (int successorOffset : currentBlockInfo.exceptionalSuccessors) {
          live.putAll(getLocalVariables(successorOffset).locals);
        }
        for (Entry<DebugLocalInfo> entry : incomingLocals.int2ReferenceEntrySet()) {
          if (live.get(entry.getIntKey()) != entry.getValue()) {
            builder.addDebugLocalEnd(entry.getIntKey(), entry.getValue());
          }
        }
      }
    } else if (localsChanged && hasNextInstructionInCurrentBlock) {
      endLocals(builder);
    }

    build(instruction, builder);
    if (!hasNextInstructionInCurrentBlock) {
      Snapshot stateSnapshot = state.getSnapshot();
      if (isControlFlow(instruction)) {
        for (int target : getTargets(instructionIndex)) {
          recordStateForTarget(target, stateSnapshot);
        }
      } else {
        recordStateForTarget(instructionIndex + 1, stateSnapshot);
      }
    } else if (localsChanged) {
      startLocals(builder);
    }
  }

  private void build(CfInstruction instruction, IRBuilder builder) {
    instruction.buildIR(builder, state, this);
  }

  private void recordStateForTarget(int target, Snapshot snapshot) {
    Snapshot existing = incomingState.get(target);
    Snapshot merged = CfState.merge(existing, snapshot, origin);
    if (merged != existing) {
      incomingState.put(target, merged);
    }
  }

  public int getCurrentInstructionIndex() {
    return currentInstructionIndex;
  }

  public int getLabelOffset(CfLabel label) {
    assert labelOffsets.containsKey(label);
    return labelOffsets.getInt(label);
  }

  public void setStateFromFrame(CfFrame frame) {
    Int2ObjectSortedMap<FrameType> frameLocals = frame.getLocals();
    DexType[] locals = new DexType[frameLocals.isEmpty() ? 0 : frameLocals.lastIntKey() + 1];
    DexType[] stack = new DexType[frame.getStack().size()];
    frame.forEachLocal(
        (localIndex, frameType) -> locals[localIndex] = convertUninitialized(frameType));
    int index = 0;
    for (PreciseFrameType frameType : frame.getStack()) {
      stack[index++] = convertUninitialized(frameType);
    }
    // TODO(b/169135126) Assert that all values are precise.
    Snapshot snapshot =
        state.setStateFromFrame(
            locals, stack, getCanonicalDebugPositionAtOffset(currentInstructionIndex));
    // Update the incoming state as well with precise information.
    assert incomingState.get(currentBlockIndex) != null;
    if (isFirstFrameInBlock()) {
      incomingState.put(currentBlockIndex, snapshot);
    }
  }

  private boolean isFirstFrameInBlock() {
    for (int i = currentBlockIndex; i < currentInstructionIndex; i++) {
      CfInstruction cfInstruction = code.getInstructions().get(i);
      if (cfInstruction.isPosition() || cfInstruction.isLabel()) {
        continue;
      }
      return false;
    }
    return true;
  }

  private DexType convertUninitialized(FrameType type) {
    if (type.isInitializedReferenceType()) {
      if (type.isNullType()) {
        return type.asNullType().getInitializedType();
      } else {
        assert type.isInitializedNonNullReferenceTypeWithoutInterfaces();
        return type.asInitializedNonNullReferenceTypeWithoutInterfaces().getInitializedType();
      }
    }
    if (type.isPrimitive()) {
      if (type.isSinglePrimitive()) {
        return type.asSinglePrimitive().getInitializedType(appView.dexItemFactory());
      } else {
        assert type.isWidePrimitive();
        return type.asWidePrimitive().getLowType().getInitializedType(appView.dexItemFactory());
      }
    }
    if (type.isUninitializedNew()) {
      int labelOffset = getLabelOffset(type.getUninitializedLabel());
      int insnOffset = labelOffset + 1;
      while (insnOffset < code.getInstructions().size()) {
        CfInstruction instruction = code.getInstructions().get(insnOffset);
        if (!(instruction instanceof CfLabel)
            && !(instruction instanceof CfFrame)
            && !(instruction instanceof CfPosition)) {
          assert instruction instanceof CfNew;
          break;
        }
        insnOffset += 1;
      }
      CfInstruction instruction = code.getInstructions().get(insnOffset);
      assert instruction instanceof CfNew;
      return ((CfNew) instruction).getType();
    }
    if (type.isUninitializedThis()) {
      return method.getHolderType();
    }
    assert type.isOneWord();
    return null;
  }

  @Override
  public void resolveAndBuildSwitch(
      int value, int fallthroughOffset, int payloadOffset, IRBuilder builder) {}

  @Override
  public void resolveAndBuildNewArrayFilledData(
      int arrayRef, int payloadOffset, IRBuilder builder) {}

  @Override
  public DebugLocalInfo getIncomingLocalAtBlock(int register, int blockOffset) {
    return getLocalVariables(blockOffset).locals.get(register);
  }

  @Override
  public DexType getPhiTypeForBlock(
      int register, int blockOffset, ValueTypeConstraint constraint, RegisterReadType readType) {
    assert code.getStackMapStatus() != CfFrameVerifier.StackMapStatus.NOT_VERIFIED;
    if (code.getStackMapStatus().isInvalidOrNotPresent()) {
      return null;
    }
    // We should be able to find the a snapshot at the block-offset:
    Snapshot snapshot = incomingState.get(blockOffset);
    if (snapshot == null) {
      appView
          .options()
          .reporter
          .warning(
              new CfCodeDiagnostics(
                  origin,
                  method.getReference(),
                  "Could not find stack map for block at offset "
                      + blockOffset
                      + ". This is most likely due to invalid"
                      + " stack maps in input."));
      return null;
    }
    // TODO(b/169135126) Assert that all values are precise.
    Slot slot =
        Slot.isStackSlot(register)
            ? snapshot.getStack(Slot.stackPosition(register))
            : snapshot.getLocal(register);
    if (slot == null) {
      if (readType == RegisterReadType.DEBUG) {
        DebugLocalInfo incomingLocalAtBlock = getIncomingLocalAtBlock(register, blockOffset);
        if (incomingLocalAtBlock != null) {
          return incomingLocalAtBlock.type;
        }
        // TODO(b/b/169137397): The local ranges are not defined on the block. We should investigate
        //   the impact of this when debugging. For now, make a final attempt at finding a local
        //   variable with specified register.
        List<LocalVariableInfo> localVariablesWithRegister = new ArrayList<>();
        for (LocalVariableInfo variable : localVariables) {
          if (variable.getIndex() == register) {
            localVariablesWithRegister.add(variable);
          }
        }
        if (localVariablesWithRegister.size() == 1) {
          return localVariablesWithRegister.get(0).getLocal().type;
        }
      }
      // TODO(b/169346184): Delay reporting errors here due to invalid debug info until resolved.
      // appView
      //     .options()
      //     .reporter
      //     .warning(
      //         new CfCodeDiagnostics(
      //             origin,
      //             method.getReference(),
      //             "Could not find phi type for register "
      //                 + register
      //                 + ". This is most likely due to invalid stack maps in input."));
      return null;
    }
    if (slot.isPrecise()) {
      return slot.preciseType;
    }
    // TODO(b/169135126): We should be able to remove this when having valid stack maps.
    return slot.type.isObject()
        ? appView.dexItemFactory().objectType
        : slot.type.toPrimitiveType().toDexType(appView.dexItemFactory());
  }

  @Override
  public DebugLocalInfo getIncomingLocal(int register) {
    return isCurrentlyGeneratingMethodSynchronization() ? null : incomingLocals.get(register);
  }

  @Override
  public DebugLocalInfo getOutgoingLocal(int register) {
    if (isCurrentlyGeneratingMethodSynchronization()) {
      return null;
    }
    if (inPrelude) {
      return getIncomingLocal(register);
    }
    assert !isControlFlow(code.getInstructions().get(currentInstructionIndex))
        : "Outgoing local is undefined for control-flow instructions";
    return outgoingLocals.get(register);
  }

  private void setLocalVariableLists() {
    incomingLocals = getLocalVariables(currentInstructionIndex).locals;
    if (inPrelude) {
      outgoingLocals = incomingLocals;
      return;
    }
    CfInstruction currentInstruction = code.getInstructions().get(currentInstructionIndex);
    outgoingLocals =
        !isControlFlow(currentInstruction)
            ? getLocalVariables(currentInstructionIndex + 1).locals
            : emptyMap();
  }

  private boolean localsChanged() {
    return !incomingLocals.equals(outgoingLocals);
  }

  private void endLocals(IRBuilder builder) {
    assert localsChanged();
    for (Entry<DebugLocalInfo> entry : incomingLocals.int2ReferenceEntrySet()) {
      if (!entry.getValue().equals(outgoingLocals.get(entry.getIntKey()))) {
        builder.addDebugLocalEnd(entry.getIntKey(), entry.getValue());
      }
    }
  }

  private void startLocals(IRBuilder builder) {
    assert localsChanged();
    for (Entry<DebugLocalInfo> entry : outgoingLocals.int2ReferenceEntrySet()) {
      if (!entry.getValue().equals(incomingLocals.get(entry.getIntKey()))) {
        Slot slot = state.read(entry.getIntKey());
        if (slot != null && slot.type != ValueType.fromDexType(entry.getValue().type)) {
          throw new InvalidDebugInfoException(
              "Attempt to define local of type "
                  + prettyType(slot.type)
                  + " as "
                  + entry.getValue().toString(PrintLevel.FULL));
        }
        builder.addDebugLocalStart(entry.getIntKey(), entry.getValue());
      }
    }
  }

  private String prettyType(ValueType type) {
    switch (type) {
      case OBJECT:
        return "reference";
      case INT:
        return "int";
      case FLOAT:
        return "float";
      case LONG:
        return "long";
      case DOUBLE:
        return "double";
      default:
        throw new Unreachable();
    }
  }

  private boolean isControlFlow(CfInstruction currentInstruction) {
    return currentInstruction.isReturn()
        || currentInstruction.getTarget() != null
        || currentInstruction instanceof CfSwitch
        || currentInstruction instanceof CfThrow;
  }

  @Override
  public CatchHandlers<Integer> getCurrentCatchHandlers(IRBuilder builder) {
    if (inPrelude) {
      return null;
    }
    if (isCurrentlyGeneratingMethodSynchronization()) {
      return null;
    }
    TryHandlerList tryHandlers =
        getTryHandlers(
            instructionOffset(currentInstructionIndex), builder.appView.dexItemFactory());
    if (tryHandlers.isEmpty()) {
      return null;
    }
    return new CatchHandlers<>(tryHandlers.guards, tryHandlers.offsets);
  }

  @Override
  public int getMoveExceptionRegister(int instructionIndex) {
    return CfState.Slot.STACK_OFFSET;
  }

  @Override
  public boolean verifyCurrentInstructionCanThrow() {
    return isCurrentlyGeneratingMethodSynchronization()
        // In the prelude we may be materializing arguments from call sites in R8.
        || inPrelude
        || code.getInstructions().get(currentInstructionIndex).canThrow();
  }

  @Override
  public boolean verifyLocalInScope(DebugLocalInfo local) {
    return false;
  }

  @Override
  public boolean hasValidTypesFromStackMap() {
    return code.getStackMapStatus() == CfFrameVerifier.StackMapStatus.VALID;
  }

  @Override
  public Position getCanonicalDebugPositionAtOffset(int offset) {
    if (offset == EXCEPTIONAL_SYNC_EXIT_OFFSET) {
      return canonicalPositions.getExceptionalExitPosition(
          appView.options().debug,
          () ->
              code.getInstructions().stream()
                  .filter(insn -> insn instanceof CfPosition)
                  .map(insn -> ((CfPosition) insn).getPosition())
                  .collect(Collectors.toList()),
          method.getReference());
    }
    while (offset + 1 < code.getInstructions().size()) {
      CfInstruction insn = code.getInstructions().get(offset);
      if (!(insn instanceof CfLabel) && !(insn instanceof CfFrame)) {
        break;
      }
      offset += 1;
    }
    while (offset >= 0 && !(code.getInstructions().get(offset) instanceof CfPosition)) {
      offset -= 1;
    }
    if (offset < 0) {
      return canonicalPositions.getPreamblePosition();
    }
    return getCanonicalPosition(((CfPosition) code.getInstructions().get(offset)).getPosition());
  }

  @Override
  public Position getCurrentPosition() {
    return state.getPosition();
  }

  public Position getCanonicalPosition(Position position) {
    return canonicalPositions.getCanonical(
        position
            .builderWithCopy()
            .setCallerPosition(
                canonicalPositions.canonicalizeCallerPosition(position.getCallerPosition()))
            .build());
  }
}
