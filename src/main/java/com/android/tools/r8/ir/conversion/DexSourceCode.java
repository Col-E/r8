// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.dex.code.DexFillArrayData;
import com.android.tools.r8.dex.code.DexFillArrayDataPayload;
import com.android.tools.r8.dex.code.DexFilledNewArray;
import com.android.tools.r8.dex.code.DexFilledNewArrayRange;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexInvokeCustom;
import com.android.tools.r8.dex.code.DexInvokeCustomRange;
import com.android.tools.r8.dex.code.DexInvokeDirect;
import com.android.tools.r8.dex.code.DexInvokeDirectRange;
import com.android.tools.r8.dex.code.DexInvokeInterface;
import com.android.tools.r8.dex.code.DexInvokeInterfaceRange;
import com.android.tools.r8.dex.code.DexInvokePolymorphic;
import com.android.tools.r8.dex.code.DexInvokePolymorphicRange;
import com.android.tools.r8.dex.code.DexInvokeStatic;
import com.android.tools.r8.dex.code.DexInvokeStaticRange;
import com.android.tools.r8.dex.code.DexInvokeSuper;
import com.android.tools.r8.dex.code.DexInvokeSuperRange;
import com.android.tools.r8.dex.code.DexInvokeVirtual;
import com.android.tools.r8.dex.code.DexInvokeVirtualRange;
import com.android.tools.r8.dex.code.DexMoveException;
import com.android.tools.r8.dex.code.DexMoveResult;
import com.android.tools.r8.dex.code.DexMoveResultObject;
import com.android.tools.r8.dex.code.DexMoveResultWide;
import com.android.tools.r8.dex.code.DexSwitchPayload;
import com.android.tools.r8.dex.code.DexThrow;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexCode.TryHandler.TypeAddrPair;
import com.android.tools.r8.graph.DexDebugEntry;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.CanonicalPositions;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.DexDebugUtils;

import java.util.*;
import java.util.function.BiConsumer;

public class DexSourceCode implements SourceCode {

  private final DexCode code;
  private final ProgramMethod method;

  // Mapping from instruction offset to instruction index in the DexCode instruction array.
  private final Map<Integer, Integer> offsetToInstructionIndex = new HashMap<>();

  private final SwitchPayloadResolver switchPayloadResolver = new SwitchPayloadResolver();
  private final ArrayFilledDataPayloadResolver arrayFilledDataPayloadResolver =
      new ArrayFilledDataPayloadResolver();

  private Try currentTryRange = null;
  private CatchHandlers<Integer> currentCatchHandlers = null;
  private DexInstruction currentDexInstruction = null;
  private boolean isBuildingPrelude;

  private Position currentPosition = null;
  private final CanonicalPositions canonicalPositions;

  private List<DexDebugEntry> debugEntries = null;
  // In case of inlining the position of the invoke in the caller.
  private final DexMethod originalMethod;

  public DexSourceCode(
      DexCode code,
      ProgramMethod method,
      DexMethod originalMethod,
      Position callerPosition,
      DexItemFactory factory) {
    this.code = code;
    this.method = method;
    this.originalMethod = originalMethod;
    EventBasedDebugInfo info = DexDebugInfo.convertToEventBased(code, factory);
    if (info != null) {
      debugEntries = info.computeEntries(originalMethod);
    }
    canonicalPositions =
        new CanonicalPositions(
            callerPosition,
            debugEntries == null ? 0 : debugEntries.size(),
            originalMethod,
            method.getDefinition().isD8R8Synthesized(),
            DexDebugUtils.computePreamblePosition(originalMethod, info).getFramePosition());
  }

  @Override
  public boolean verifyRegister(int register) {
    return register < code.registerSize;
  }

  @Override
  public int instructionCount() {
    return code.instructions.length;
  }

  @Override
  public DebugLocalInfo getIncomingLocalAtBlock(int register, int blockOffset) {
    return null;
  }

  @Override
  public DebugLocalInfo getIncomingLocal(int register) {
    return null;
  }

  @Override
  public DebugLocalInfo getOutgoingLocal(int register) {
    return null;
  }

  @Override
  public void setUp() {
    // Collect all payloads in the instruction stream.
    for (int index = 0; index < code.instructions.length; index++) {
      DexInstruction insn = code.instructions[index];
      offsetToInstructionIndex.put(insn.getOffset(), index);
      if (insn.isPayload()) {
        if (insn.isSwitchPayload()) {
          switchPayloadResolver.resolve((DexSwitchPayload) insn);
        } else {
          arrayFilledDataPayloadResolver.resolve((DexFillArrayDataPayload) insn);
        }
      }
    }
  }

  @Override
  public void buildPrelude(IRBuilder builder) {
    assert !isBuildingPrelude;
    isBuildingPrelude = true;
    currentPosition = canonicalPositions.getPreamblePosition();
    if (code.incomingRegisterSize > 0) {
      builder.buildArgumentsWithRewrittenPrototypeChanges(
          code.registerSize - code.incomingRegisterSize,
          method.getDefinition(),
          DexSourceCode::doNothingWriteConsumer);
    }
    isBuildingPrelude = false;
  }

  public static void doNothingWriteConsumer(Integer register, DexType type) {
    // Intentionally empty.
  }

  @Override
  public void buildPostlude(IRBuilder builder) {
    // Intentionally left empty. (Needed in the Java bytecode frontend for synchronization support.)
  }

  @Override
  public void buildBlockTransfer(
      IRBuilder builder, int predecessorOffset, int successorOffset, boolean isExceptional) {
    // Intentionally empty. Dex front-end does not support debug locals so no transfer info needed.
  }

  @Override
  public void buildInstruction(
      IRBuilder builder, int instructionIndex, boolean firstBlockInstruction) {
    updateCurrentCatchHandlers(instructionIndex, builder.appView.dexItemFactory());
    updateDebugPosition(instructionIndex, builder);
    currentDexInstruction = code.instructions[instructionIndex];
    currentDexInstruction.buildIR(builder);
  }

  @Override
  public CatchHandlers<Integer> getCurrentCatchHandlers(IRBuilder builder) {
    return currentCatchHandlers;
  }

  @Override
  public int getMoveExceptionRegister(int instructionIndex) {
    DexInstruction instruction = code.instructions[instructionIndex];
    if (instruction instanceof DexMoveException) {
      DexMoveException moveException = (DexMoveException) instruction;
      return moveException.AA;
    }
    return -1;
  }

  @Override
  public Position getCanonicalDebugPositionAtOffset(int offset) {
    DexDebugEntry entry = getDebugEntryAtOffset(offset);
    return entry == null
        ? canonicalPositions.getPreamblePosition()
        : getCanonicalPositionAppendCaller(entry);
  }

  @Override
  public Position getCurrentPosition() {
    return currentPosition;
  }

  @Override
  public boolean verifyCurrentInstructionCanThrow() {
    // In the prelude we may be materializing arguments from call sites in R8.
    return isBuildingPrelude || currentDexInstruction.canThrow();
  }

  @Override
  public boolean verifyLocalInScope(DebugLocalInfo local) {
    return true;
  }

  private void updateCurrentCatchHandlers(int instructionIndex, DexItemFactory factory) {
    Try tryRange = getTryForOffset(instructionOffset(instructionIndex));
    if (Objects.equals(tryRange, currentTryRange)) {
      return;
    }
    currentTryRange = tryRange;
    if (tryRange == null) {
      currentCatchHandlers = null;
    } else {
      currentCatchHandlers = getCurrentCatchHandlers(factory, tryRange);
    }
  }

  private DexDebugEntry getDebugEntryAtOffset(int offset) {
    DexDebugEntry current = null;
    if (debugEntries != null) {
      for (DexDebugEntry entry : debugEntries) {
        if (entry.address > offset) {
          break;
        }
        current = entry;
      }
    }
    return current;
  }

  private void updateDebugPosition(int instructionIndex, IRBuilder builder) {
    if (debugEntries == null || debugEntries.isEmpty()) {
      return;
    }
    int offset = instructionOffset(instructionIndex);
    DexDebugEntry entry = getDebugEntryAtOffset(offset);
    if (entry == null) {
      currentPosition = canonicalPositions.getPreamblePosition();
    } else {
      currentPosition = getCanonicalPositionAppendCaller(entry);
      if (entry.lineEntry && entry.address == offset) {
        builder.addDebugPosition(currentPosition);
      }
    }
  }

  private Position getCanonicalPositionAppendCaller(DexDebugEntry entry) {
    // If this instruction has already been inlined then the original method must be in the caller
    // chain.
    Position position = entry.getPosition();
    // TODO(b/261971803): The original method should probably always be in the chain.
    assert !position.hasCallerPosition() || position.hasMethodInChain(originalMethod);
    return canonicalPositions.getCanonical(
        position
            .builderWithCopy()
            .setCallerPosition(
                canonicalPositions.canonicalizeCallerPosition(position.getCallerPosition()))
            .build());
  }

  @Override
  public void clear() {
    switchPayloadResolver.clear();
    arrayFilledDataPayloadResolver.clear();
  }

  @Override
  public int instructionIndex(int instructionOffset) {
    return offsetToInstructionIndex.get(instructionOffset);
  }

  @Override
  public int instructionOffset(int instructionIndex) {
    return code.instructions[instructionIndex].getOffset();
  }

  @Override
  public void resolveAndBuildSwitch(int value, int fallthroughOffset, int payloadOffset,
      IRBuilder builder) {
    builder.addSwitch(value, switchPayloadResolver.getKeys(payloadOffset), fallthroughOffset,
        switchPayloadResolver.absoluteTargets(payloadOffset));
  }

  @Override
  public void resolveAndBuildNewArrayFilledData(int arrayRef, int payloadOffset,
      IRBuilder builder) {
    builder.addNewArrayFilledData(arrayRef,
        arrayFilledDataPayloadResolver.getElementWidth(payloadOffset),
        arrayFilledDataPayloadResolver.getSize(payloadOffset),
        arrayFilledDataPayloadResolver.getData(payloadOffset));
  }

  private boolean isInvoke(DexInstruction dex) {
    return dex instanceof DexInvokeCustom
        || dex instanceof DexInvokeCustomRange
        || dex instanceof DexInvokeDirect
        || dex instanceof DexInvokeDirectRange
        || dex instanceof DexInvokeVirtual
        || dex instanceof DexInvokeVirtualRange
        || dex instanceof DexInvokeInterface
        || dex instanceof DexInvokeInterfaceRange
        || dex instanceof DexInvokeStatic
        || dex instanceof DexInvokeStaticRange
        || dex instanceof DexInvokeSuper
        || dex instanceof DexInvokeSuperRange
        || dex instanceof DexInvokePolymorphic
        || dex instanceof DexInvokePolymorphicRange
        || dex instanceof DexFilledNewArray
        || dex instanceof DexFilledNewArrayRange;
  }

  private boolean isMoveResult(DexInstruction dex) {
    return dex instanceof DexMoveResult
        || dex instanceof DexMoveResultObject
        || dex instanceof DexMoveResultWide;
  }

  @Override
  public int traceInstruction(int index, IRBuilder builder) {
    DexInstruction dex = code.instructions[index];
    int offset = dex.getOffset();
    assert !dex.isPayload();
    int[] targets = dex.getTargets();
    if (targets != DexInstruction.NO_TARGETS) {
      // Check that we don't ever have instructions that can throw and have targets.
      assert !dex.canThrow();
      for (int relativeOffset : targets) {
        builder.ensureNormalSuccessorBlock(offset, offset + relativeOffset);
      }
      return index;
    }
    if (dex.canThrow()) {
      // TODO(zerny): Remove this from block computation.
      if (dex.hasPayload()) {
        arrayFilledDataPayloadResolver.addPayloadUser((DexFillArrayData) dex);
      }
      // If the instruction can throw and is in a try block, add edges to its catch successors.
      Try tryRange = getTryForOffset(offset);
      if (tryRange != null) {
        // Ensure the block starts at the start of the try-range (don't enqueue, not a target).
        int tryRangeStartAddress = tryRange.startAddress;
        if (isMoveResult(code.instructions[offsetToInstructionIndex.get(tryRangeStartAddress)])) {
          // If a handler range starts at a move result instruction it is safe to start it at
          // the following instruction since the move-result cannot throw an exception. Doing so
          // makes sure that we do not split an invoke and its move result instruction across
          // two blocks.
          ++tryRangeStartAddress;
        }
        builder.ensureBlockWithoutEnqueuing(tryRangeStartAddress);
        // Edge to exceptional successors.
        for (Integer handlerOffset :
            getUniqueTryHandlerOffsets(tryRange, builder.appView.dexItemFactory())) {
          builder.ensureExceptionalSuccessorBlock(offset, handlerOffset);
        }
        // If the following instruction is a move-result include it in this (the invokes) block.
        if (index + 1 < code.instructions.length && isMoveResult(code.instructions[index + 1])) {
          assert isInvoke(dex);
          ++index;
          dex = code.instructions[index];
        }
        // Edge to normal successor if any (fallthrough).
        if (!(dex instanceof DexThrow)) {
          builder.ensureNormalSuccessorBlock(offset, dex.getOffset() + dex.getSize());
        }
        return index;
      }
      // Close the block if the instruction is a throw, otherwise the block remains open.
      return dex instanceof DexThrow ? index : -1;
    }
    if (dex.isIntSwitch()) {
      // TODO(zerny): Remove this from block computation.
      switchPayloadResolver.addPayloadUser(dex);

      for (int target : switchPayloadResolver.absoluteTargets(dex)) {
        builder.ensureNormalSuccessorBlock(offset, target);
      }
      builder.ensureNormalSuccessorBlock(offset, offset + dex.getSize());
      return index;
    }
    // This instruction does not close the block.
    return -1;
  }

  private boolean inTryRange(Try tryItem, int offset) {
    return tryItem.startAddress <= offset
        && offset < tryItem.startAddress + tryItem.instructionCount;
  }

  private Try getTryForOffset(int offset) {
    for (Try tryRange : code.tries) {
      if (inTryRange(tryRange, offset)) {
        return tryRange;
      }
    }
    return null;
  }

  private CatchHandlers<Integer> getCurrentCatchHandlers(DexItemFactory factory, Try tryRange) {
    List<DexType> handlerGuards = new ArrayList<>();
    List<Integer> handlerOffsets = new ArrayList<>();
    forEachTryRange(
        tryRange,
        factory,
        (type, addr) -> {
          handlerGuards.add(type);
          handlerOffsets.add(addr);
        });
    return new CatchHandlers<>(handlerGuards, handlerOffsets);
  }

  private void forEachTryRange(
      Try tryRange, DexItemFactory factory, BiConsumer<DexType, Integer> fn) {
    TryHandler handler = code.handlers[tryRange.handlerIndex];
    for (TypeAddrPair pair : handler.pairs) {
      fn.accept(pair.getType(), pair.addr);
      if (pair.getType() == factory.throwableType) {
        return;
      }
    }
    if (handler.catchAllAddr != TryHandler.NO_HANDLER) {
      fn.accept(factory.throwableType, handler.catchAllAddr);
    }

  }

  private Set<Integer> getUniqueTryHandlerOffsets(Try tryRange, DexItemFactory factory) {
    return new HashSet<>(getTryHandlerOffsets(tryRange, factory));
  }

  private List<Integer> getTryHandlerOffsets(Try tryRange, DexItemFactory factory) {
    List<Integer> handlerOffsets = new ArrayList<>();
    forEachTryRange(tryRange, factory, (type, addr) -> handlerOffsets.add(addr));
    return handlerOffsets;
  }
}
