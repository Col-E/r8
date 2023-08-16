// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.FailedDataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.SuccessfulDataflowAnalysisResult;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.TraversalUtils;
import com.android.tools.r8.utils.WorkList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * This defines a simple fixpoint solver for running an intraprocedural dataflow analysis.
 *
 * <p>The solver computes an {@link AbstractState} for each {@link Block} using the {@link
 * AbstractTransferFunction} which defines the abstract semantics for each instruction.
 *
 * <p>Once the fixpoint is reached the analysis returns a {@link SuccessfulDataflowAnalysisResult}.
 * If the supplied {@link AbstractTransferFunction} returns a {@link FailedTransferFunctionResult}
 * for a given instruction and abstract state, then the analysis return a {@link
 * FailedDataflowAnalysisResult}.
 */
public class IntraProceduralDataflowAnalysisBase<
    Block, Instruction extends AbstractInstruction, StateType extends AbstractState<StateType>> {

  final AppView<?> appView;

  final StateType bottom;

  final ControlFlowGraph<Block, Instruction> cfg;

  // The transfer function that defines the abstract semantics for each instruction.
  final AbstractTransferFunction<Block, Instruction, StateType> transfer;

  // The entry states for each block that satisfies the predicate
  // shouldCacheBlockEntryStateFor(block). These entry states can be computed from the exit states
  // of the predecessors, but doing so can be expensive when a block has many predecessors.
  final Map<Block, StateType> blockEntryStates = new IdentityHashMap<>();

  // The state of the analysis.
  final Map<Block, StateType> blockExitStates = new IdentityHashMap<>();

  // The entry states for exceptional blocks.
  final Map<Block, StateType> exceptionalBlockEntryStates = new IdentityHashMap<>();

  final IntraProceduralDataflowAnalysisOptions options;

  public IntraProceduralDataflowAnalysisBase(
      AppView<?> appView,
      StateType bottom,
      ControlFlowGraph<Block, Instruction> cfg,
      AbstractTransferFunction<Block, Instruction, StateType> transfer,
      IntraProceduralDataflowAnalysisOptions options) {
    this.appView = appView;
    this.bottom = bottom;
    this.cfg = cfg;
    this.transfer = transfer;
    this.options = options;
  }

  public DataflowAnalysisResult run(Block root) {
    return run(root, Timing.empty());
  }

  public DataflowAnalysisResult run(Block root, Timing timing) {
    return run(WorkList.newIdentityWorkList(root), timing);
  }

  private DataflowAnalysisResult run(WorkList<Block> worklist, Timing timing) {
    while (worklist.hasNext()) {
      Block initialBlock = worklist.removeSeen();
      Block block = initialBlock;
      Block end = null;
      // Compute the abstract state upon entry to the basic block, by joining all the predecessor
      // exit states.
      StateType state =
          timing.time("Compute block entry state", () -> computeBlockEntryState(initialBlock));

      TransferFunctionResult<StateType> blockResult = transfer.applyBlock(initialBlock, state);
      if (blockResult.isFailedTransferResult()) {
        return transfer.createFailedAnalysisResult(null, state);
      }
      state = blockResult.asAbstractState();

      timing.begin("Compute transfers");
      do {
        Block currentBlock = block;
        boolean hasExceptionalSuccessors = cfg.hasExceptionalSuccessors(block);
        TraversalContinuation<FailedDataflowAnalysisResult, StateType> traversalContinuation =
            cfg.traverseInstructions(
                block,
                (instruction, previousState) -> {
                  if (instruction.instructionTypeCanThrow()
                      && hasExceptionalSuccessors
                      && transfer.shouldTransferExceptionalControlFlowFromInstruction(
                          currentBlock, instruction)) {
                    updateBlockEntryStateCacheForExceptionalSuccessors(
                        currentBlock, instruction, previousState);
                  }
                  TransferFunctionResult<StateType> transferResult =
                      transfer.apply(instruction, previousState);
                  if (transferResult.isFailedTransferResult()) {
                    return TraversalContinuation.doBreak(
                        transfer.createFailedAnalysisResult(instruction, transferResult));
                  }
                  assert transferResult.isAbstractState();
                  return TraversalContinuation.doContinue(transferResult.asAbstractState());
                },
                state);
        if (traversalContinuation.isBreak()) {
          timing.end();
          return traversalContinuation.asBreak().getValue();
        }
        state = traversalContinuation.asContinue().getValue();
        if (isBlockWithIntermediateSuccessorBlock(block)) {
          block = cfg.getUniqueSuccessor(block);
        } else {
          end = block;
          block = null;
        }
      } while (block != null);
      timing.end();

      // Update the block exit state, and re-enqueue all successor blocks if the abstract state
      // changed.
      if (setBlockExitState(end, state)) {
        cfg.forEachSuccessor(end, worklist::addIfNotSeen);
      }

      // Add the computed exit state to the entry state of each normal successor that satisfies the
      // predicate shouldCacheBlockEntryStateFor(successor).
      updateBlockEntryStateCacheForNormalSuccessors(end, state);
    }
    return new SuccessfulDataflowAnalysisResult<>(blockExitStates);
  }

  public StateType computeBlockEntryState(Block block) {
    if (block == cfg.getEntryBlock()) {
      return transfer
          .computeInitialState(block, bottom)
          .join(appView, computeBlockEntryStateForNormalBlock(block));
    }
    if (cfg.hasExceptionalPredecessors(block)) {
      return exceptionalBlockEntryStates.getOrDefault(block, bottom).clone();
    }
    return computeBlockEntryStateForNormalBlock(block);
  }

  private StateType computeBlockEntryStateForNormalBlock(Block block) {
    if (shouldCacheBlockEntryStateForNormalBlock(block)) {
      return blockEntryStates.getOrDefault(block, bottom).clone();
    }
    return computeBlockEntryStateFromPredecessorExitStates(block);
  }

  private StateType computeBlockEntryStateFromPredecessorExitStates(Block block) {
    TraversalContinuation<?, StateType> traversalContinuation =
        cfg.traverseNormalPredecessors(
            block,
            (predecessor, entryState) -> {
              StateType edgeState =
                  transfer.computeBlockEntryState(
                      block,
                      predecessor,
                      blockExitStates.getOrDefault(predecessor, bottom).clone());
              return TraversalContinuation.doContinue(entryState.join(appView, edgeState));
            },
            bottom);
    return traversalContinuation.asContinue().getValue().clone();
  }

  boolean setBlockExitState(Block block, StateType state) {
    assert !isBlockWithIntermediateSuccessorBlock(block);
    StateType previous = blockExitStates.put(block, state);
    assert previous == null || state.isGreaterThanOrEquals(appView, previous);
    return !state.equals(previous);
  }

  void updateBlockEntryStateCacheForNormalSuccessors(Block block, StateType state) {
    cfg.forEachNormalSuccessor(
        block,
        successor -> {
          if (shouldCacheBlockEntryStateForNormalBlock(successor)) {
            StateType edgeState = transfer.computeBlockEntryState(successor, block, state);
            updateBlockEntryStateForBlock(successor, edgeState, blockEntryStates);
          }
        });
  }

  void updateBlockEntryStateCacheForExceptionalSuccessors(
      Block block, Instruction instruction, StateType state) {
    cfg.forEachExceptionalSuccessor(
        block,
        (exceptionalSuccessor, guard) -> {
          StateType edgeState =
              transfer.computeExceptionalBlockEntryState(
                  exceptionalSuccessor, guard, block, instruction, state);
          updateBlockEntryStateForBlock(
              exceptionalSuccessor, edgeState, exceptionalBlockEntryStates);
        });
  }

  private void updateBlockEntryStateForBlock(
      Block block, StateType edgeState, Map<Block, StateType> states) {
    StateType previous = states.getOrDefault(block, bottom);
    states.put(block, previous.join(appView, edgeState));
  }

  public boolean isIntermediateBlock(Block block) {
    return options.isCollapsingOfTrivialEdgesEnabled()
        && cfg.hasUniquePredecessorWithUniqueSuccessor(block)
        && block != cfg.getEntryBlock()
        && !cfg.hasExceptionalPredecessors(block);
  }

  public boolean isBlockWithIntermediateSuccessorBlock(Block block) {
    return cfg.hasUniqueSuccessor(block) && isIntermediateBlock(cfg.getUniqueSuccessor(block));
  }

  boolean shouldCacheBlockEntryStateForNormalBlock(Block block) {
    return TraversalUtils.isSizeGreaterThan(counter -> cfg.traversePredecessors(block, counter), 2);
  }
}
