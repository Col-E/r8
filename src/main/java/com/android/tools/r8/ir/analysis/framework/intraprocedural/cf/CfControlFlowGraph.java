// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural.cf;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.ControlFlowGraph;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.cf.CfBlock.MutableCfBlock;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.TraversalUtils;
import com.android.tools.r8.utils.TriFunction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The following provides a control flow graph for a piece of {@link CfCode}.
 *
 * <p>In the {@link CfControlFlowGraph}, each instruction that is the target of a jump (including
 * fallthrough targets following jumps) starts a new basic block. The first instruction in {@link
 * CfCode} also starts a new block.
 *
 * <p>Each block is identified by the first instruction of the block.
 */
public class CfControlFlowGraph implements ControlFlowGraph<CfBlock, CfInstruction> {

  // Mapping from block entry instructions to cf blocks.
  private final Map<CfInstruction, ? extends CfBlock> blocks;
  private final CfCode code;

  private CfControlFlowGraph(Map<CfInstruction, ? extends CfBlock> blocks, CfCode code) {
    this.blocks = blocks;
    this.code = code;
  }

  private static Builder builder(CfCode code) {
    return new Builder(code);
  }

  public static CfControlFlowGraph create(CfCode code, InternalOptions options) {
    return builder(code).build(options);
  }

  private CfBlock getBlock(CfInstruction blockEntry) {
    assert blocks.containsKey(blockEntry);
    return blocks.get(blockEntry);
  }

  @Override
  public Collection<? extends CfBlock> getBlocks() {
    return blocks.values();
  }

  @Override
  public CfBlock getEntryBlock() {
    return getBlock(code.getInstruction(0));
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseNormalPredecessors(
      CfBlock block,
      BiFunction<? super CfBlock, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return TraversalUtils.traverseIterable(block.getPredecessors(), fn, initialValue);
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseExceptionalPredecessors(
      CfBlock block,
      BiFunction<? super CfBlock, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return TraversalUtils.traverseIterable(block.getExceptionalPredecessors(), fn, initialValue);
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseNormalSuccessors(
      CfBlock block,
      BiFunction<? super CfBlock, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    CfInstruction blockExit = block.getLastInstruction(code);
    CfInstruction fallthroughInstruction = block.getFallthroughInstruction(code);
    return blockExit.traverseNormalTargets(
        (target, value) -> fn.apply(getBlock(target), value), fallthroughInstruction, initialValue);
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseExceptionalSuccessors(
      CfBlock block,
      TriFunction<? super CfBlock, DexType, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return TraversalUtils.traverseMap(
        block.getExceptionalSuccessors(),
        (guard, exceptionalSuccessor, value) -> fn.apply(exceptionalSuccessor, guard, value),
        initialValue);
  }

  @Override
  public <BT, CT> TraversalContinuation<BT, CT> traverseInstructions(
      CfBlock block,
      BiFunction<CfInstruction, CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    TraversalContinuation<BT, CT> traversalContinuation =
        TraversalContinuation.doContinue(initialValue);
    for (int instructionIndex = block.getFirstInstructionIndex();
        instructionIndex <= block.getLastInstructionIndex();
        instructionIndex++) {
      CfInstruction instruction = code.getInstruction(instructionIndex);
      traversalContinuation = fn.apply(instruction, traversalContinuation.asContinue().getValue());
      if (traversalContinuation.shouldBreak()) {
        break;
      }
    }
    return traversalContinuation;
  }

  private static class Builder {

    // Mapping from block entry instructions to the block that starts at each such instruction.
    private final Map<CfInstruction, MutableCfBlock> blocks = new IdentityHashMap<>();

    private final CfCode code;

    Builder(CfCode code) {
      this.code = code;
    }

    CfControlFlowGraph build(InternalOptions options) {
      // Perform an initial pass over the CfCode to identify all instructions that start a new
      // block.
      createBlocks();

      // Perform a second pass over the CfCode to finalize the identified blocks. This includes
      // setting the instruction index of the last instruction of each block, which relies on having
      // identified all block entries up front.
      processBlocks();

      removeBlockForTrailingLabel();

      CfControlFlowGraph cfg = new CfControlFlowGraph(blocks, code);
      assert blocks.values().stream().allMatch(block -> block.validate(cfg, options));
      return cfg;
    }

    private void createBlocks() {
      List<CfInstruction> instructions = code.getInstructions();

      // The first instruction starts the first block.
      createBlockIfAbsent(instructions.get(0));

      // Create a block for each instruction that is targeted by a jump or fallthrough.
      for (int instructionIndex = 0; instructionIndex < instructions.size(); instructionIndex++) {
        CfInstruction instruction = instructions.get(instructionIndex);
        if (instruction.isJump()) {
          int fallthroughInstructionIndex = instructionIndex + 1;
          CfInstruction fallthroughInstruction =
              fallthroughInstructionIndex < instructions.size()
                  ? instructions.get(fallthroughInstructionIndex)
                  : null;
          instruction.forEachNormalTarget(this::createBlockIfAbsent, fallthroughInstruction);

          // Also create a block for the fallthrough instruction, though it may be unreachable.
          if (!instruction.asJump().hasFallthrough() && fallthroughInstruction != null) {
            createBlockIfAbsent(fallthroughInstruction);
          }
        }
      }

      for (CfTryCatch tryCatch : code.getTryCatchRanges()) {
        // Create a new block at the beginning and end of each try range. This is needed to ensure
        // that each block has the same set of catch handlers.
        createBlockIfAbsent(tryCatch.getStart());
        createBlockIfAbsent(tryCatch.getEnd());

        // Create a new block for the beginning of each catch handler.
        tryCatch.forEachTarget(this::createBlockIfAbsent);
      }
    }

    private void processBlocks() {
      // A collection of active catch handlers. The most recently added catch handlers take
      // precedence over catch handlers added before them.
      Deque<CfTryCatch> activeCatchHandlers = new ArrayDeque<>();

      // A collection of inactive catch handlers. The catch handlers are stored in a map where the
      // key is the label at which the catch handlers start.
      Map<CfLabel, List<CfTryCatch>> inactiveUntilCatchHandlers = new IdentityHashMap<>();

      // Initialize all catch handlers to be inactive.
      for (CfTryCatch tryCatch : code.getTryCatchRanges()) {
        inactiveUntilCatchHandlers
            .computeIfAbsent(tryCatch.getStart(), ignoreKey(ArrayList::new))
            .add(tryCatch);
      }

      // Process each instruction.
      List<CfInstruction> instructions = code.getInstructions();
      for (int instructionIndex = 0; instructionIndex < instructions.size(); instructionIndex++) {
        CfInstruction instruction = instructions.get(instructionIndex);
        MutableCfBlock block = getBlockOrNull(instruction);
        if (block != null) {
          instructionIndex =
              processBlock(
                  instruction,
                  instructionIndex,
                  block,
                  activeCatchHandlers,
                  inactiveUntilCatchHandlers);
        }
      }

      assert activeCatchHandlers.isEmpty();
      assert inactiveUntilCatchHandlers.isEmpty();
    }

    private int processBlock(
        CfInstruction instruction,
        int instructionIndex,
        MutableCfBlock block,
        Deque<CfTryCatch> activeCatchHandlers,
        Map<CfLabel, List<CfTryCatch>> inactiveUntilCatchHandlers) {
      // Record the index of the first instruction of the block.
      block.setFirstInstructionIndex(instructionIndex);

      if (instruction.isLabel()) {
        updateCatchHandlers(instruction.asLabel(), activeCatchHandlers, inactiveUntilCatchHandlers);
      }

      // Visit each instruction belonging to the current block.
      Map<DexType, CfLabel> exceptionalSuccessors = new LinkedHashMap<>();
      Iterator<CfTryCatch> activeCatchHandlerIterator = activeCatchHandlers.descendingIterator();
      while (activeCatchHandlerIterator.hasNext()) {
        CfTryCatch activeCatchHandler = activeCatchHandlerIterator.next();
        activeCatchHandler.forEach(exceptionalSuccessors::putIfAbsent);
      }

      do {
        assert !instruction.isLabel()
            || verifyCatchHandlersUnchanged(
                instruction.asLabel(), activeCatchHandlers, inactiveUntilCatchHandlers);
        if (instruction.instructionTypeCanThrow() && !block.hasThrowingInstruction()) {
          block.setFirstThrowingInstructionIndex(instructionIndex);
        }
        if (isBlockExit(instructionIndex)) {
          break;
        }
        instruction = code.getInstruction(++instructionIndex);
      } while (true);

      // Record the index of the last instruction of the block.
      block.setLastInstructionIndex(instructionIndex);

      // Add the current block as a predecessor of the successor blocks.
      CfInstruction fallthroughInstruction = block.getFallthroughInstruction(code);
      instruction.forEachNormalTarget(
          target -> getBlock(target).addPredecessor(block), fallthroughInstruction);

      // Add the current block as an exceptional predecessor of the exceptional successor blocks.
      exceptionalSuccessors.forEach(
          (guard, exceptionalSuccessor) -> {
            MutableCfBlock exceptionalSuccessorBlock = getBlock(exceptionalSuccessor);
            block.addExceptionalSuccessor(exceptionalSuccessorBlock, guard);
            exceptionalSuccessorBlock.addExceptionalPredecessor(block);
          });

      return instructionIndex;
    }

    private void removeBlockForTrailingLabel() {
      CfInstruction lastInstruction = code.getInstruction(code.getInstructions().size() - 1);
      if (lastInstruction.isLabel() && isBlockEntry(lastInstruction)) {
        blocks.remove(lastInstruction);
      }
    }

    private boolean isBlockEntry(CfInstruction instruction) {
      return blocks.containsKey(instruction);
    }

    private boolean isBlockExit(int instructionIndex) {
      int lastInstructionIndex = code.getInstructions().size() - 1;
      if (instructionIndex == lastInstructionIndex) {
        return true;
      }
      CfInstruction nextInstruction = code.getInstruction(instructionIndex + 1);
      return isBlockEntry(nextInstruction);
    }

    private void updateCatchHandlers(
        CfLabel instruction,
        Deque<CfTryCatch> activeCatchHandlers,
        Map<CfLabel, List<CfTryCatch>> inactiveUntilCatchHandlers) {
      // Remove active catch handlers that have expired at the current instruction.
      activeCatchHandlers.removeIf(
          activeCatchHandler -> activeCatchHandler.getEnd() == instruction);

      // Promote inactive catch handlers that is activated at the current instruction to active.
      List<CfTryCatch> newlyActiveCatchHandlers = inactiveUntilCatchHandlers.remove(instruction);
      if (newlyActiveCatchHandlers != null) {
        for (int i = newlyActiveCatchHandlers.size() - 1; i >= 0; i--) {
          CfTryCatch newlyActiveCatchHandler = newlyActiveCatchHandlers.get(i);
          assert newlyActiveCatchHandler.getEnd() != newlyActiveCatchHandler.getStart();
          activeCatchHandlers.addLast(newlyActiveCatchHandler);
        }
      }
    }

    private boolean verifyCatchHandlersUnchanged(
        CfLabel instruction,
        Deque<CfTryCatch> activeCatchHandlers,
        Map<CfLabel, List<CfTryCatch>> inactiveUntilCatchHandlers) {
      assert activeCatchHandlers.stream()
          .allMatch(activeCatchHandler -> activeCatchHandler.getEnd() != instruction);
      assert !inactiveUntilCatchHandlers.containsKey(instruction);
      return true;
    }

    private void createBlockIfAbsent(CfInstruction blockEntry) {
      blocks.computeIfAbsent(blockEntry, ignoreKey(MutableCfBlock::new));
    }

    private MutableCfBlock getBlock(CfInstruction blockEntry) {
      assert blocks.containsKey(blockEntry);
      return blocks.get(blockEntry);
    }

    private MutableCfBlock getBlockOrNull(CfInstruction blockEntry) {
      return blocks.get(blockEntry);
    }
  }
}
