// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

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
    Block, Instruction, StateType extends AbstractState<StateType>> {

  final StateType bottom;

  final ControlFlowGraph<Block, Instruction> cfg;

  // The transfer function that defines the abstract semantics for each instruction.
  final AbstractTransferFunction<Block, Instruction, StateType> transfer;

  // The state of the analysis.
  final Map<Block, StateType> blockExitStates = new IdentityHashMap<>();

  // The entry states for each block that satisfies the predicate
  // shouldCacheBlockEntryStateFor(block). These entry states can be computed from the exit states
  // of the predecessors, but doing so can be expensive when a block has many predecessors.
  final Map<Block, StateType> blockEntryStatesCache = new IdentityHashMap<>();

  public IntraProceduralDataflowAnalysisBase(
      StateType bottom,
      ControlFlowGraph<Block, Instruction> cfg,
      AbstractTransferFunction<Block, Instruction, StateType> transfer) {
    this.bottom = bottom;
    this.cfg = cfg;
    this.transfer = transfer;
  }

  public DataflowAnalysisResult run(Block root) {
    return run(root, Timing.empty());
  }

  public DataflowAnalysisResult run(Block root, Timing timing) {
    return run(WorkList.newIdentityWorkList(root), timing);
  }

  private DataflowAnalysisResult run(WorkList<Block> worklist, Timing timing) {
    while (worklist.hasNext()) {
      Block initialBlock = worklist.next();
      Block block = initialBlock;
      Block end = null;
      // Compute the abstract state upon entry to the basic block, by joining all the predecessor
      // exit states.
      StateType state =
          timing.time("Compute block entry state", () -> computeBlockEntryState(initialBlock));

      timing.begin("Compute transfers");
      do {
        TraversalContinuation<FailedDataflowAnalysisResult, StateType> traversalContinuation =
            cfg.traverseInstructions(
                block,
                (instruction, previousState) -> {
                  TransferFunctionResult<StateType> transferResult =
                      transfer.apply(instruction, previousState);
                  if (transferResult.isFailedTransferResult()) {
                    timing.end();
                    return TraversalContinuation.doBreak(new FailedDataflowAnalysisResult());
                  }
                  assert transferResult.isAbstractState();
                  return TraversalContinuation.doContinue(transferResult.asAbstractState());
                },
                state);
        if (traversalContinuation.isBreak()) {
          return traversalContinuation.asBreak().getValue();
        }
        state = traversalContinuation.asContinue().getValue();
        if (cfg.hasUniqueSuccessorWithUniquePredecessor(block)) {
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
        cfg.forEachSuccessor(end, worklist::addIgnoringSeenSet);
      }

      // Add the computed exit state to the entry state of each successor that satisfies the
      // predicate shouldCacheBlockEntryStateFor(successor).
      updateBlockEntryStateCacheForSuccessors(end, state);
    }
    return new SuccessfulDataflowAnalysisResult<>(blockExitStates);
  }

  StateType computeBlockEntryState(Block block) {
    if (shouldCacheBlockEntryStateFor(block)) {
      return blockEntryStatesCache.getOrDefault(block, bottom);
    }
    TraversalContinuation<?, StateType> traversalContinuation =
        cfg.traversePredecessors(
            block,
            (predecessor, entryState) -> {
              StateType edgeState =
                  transfer.computeBlockEntryState(
                      block, predecessor, blockExitStates.getOrDefault(predecessor, bottom));
              return TraversalContinuation.doContinue(entryState.join(edgeState));
            },
            bottom);
    return traversalContinuation.asContinue().getValue();
  }

  boolean setBlockExitState(Block block, StateType state) {
    assert !cfg.hasUniqueSuccessorWithUniquePredecessor(block);
    StateType previous = blockExitStates.put(block, state);
    assert previous == null || state.isGreaterThanOrEquals(previous);
    return !state.equals(previous);
  }

  void updateBlockEntryStateCacheForSuccessors(Block block, StateType state) {
    cfg.forEachSuccessor(
        block,
        successor -> {
          if (shouldCacheBlockEntryStateFor(successor)) {
            StateType edgeState = transfer.computeBlockEntryState(successor, block, state);
            StateType previous = blockEntryStatesCache.getOrDefault(successor, bottom);
            blockEntryStatesCache.put(successor, previous.join(edgeState));
          }
        });
  }

  boolean shouldCacheBlockEntryStateFor(Block block) {
    return TraversalUtils.isSizeGreaterThan(counter -> cfg.traversePredecessors(block, counter), 2);
  }
}
