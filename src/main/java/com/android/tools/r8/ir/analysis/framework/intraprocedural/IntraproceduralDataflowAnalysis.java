// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.FailedDataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.SuccessfulDataflowAnalysisResult;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.WorkList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * This defines a simple fixpoint solver for running an intraprocedural dataflow analysis.
 *
 * <p>The solver computes an {@link AbstractState} for each {@link BasicBlock} using the {@link
 * AbstractTransferFunction} which defines the abstract semantics for each instruction.
 *
 * <p>Once the fixpoint is reached the analysis returns a {@link SuccessfulDataflowAnalysisResult}.
 * If the supplied {@link AbstractTransferFunction} returns a {@link FailedTransferFunctionResult}
 * for a given instruction and abstract state, then the analysis return a {@link
 * FailedDataflowAnalysisResult}.
 */
public class IntraproceduralDataflowAnalysis<StateType extends AbstractState<StateType>> {

  private final StateType bottom;

  // The transfer function that defines the abstract semantics for each instruction.
  private final AbstractTransferFunction<StateType> transfer;

  // The state of the analysis.
  private final Map<BasicBlock, StateType> blockExitStates = new IdentityHashMap<>();

  // The entry states for each block that satisfies the predicate
  // shouldCacheBlockEntryStateFor(block). These entry states can be computed from the exit states
  // of the predecessors, but doing so can be expensive when a block has many predecessors.
  private final Map<BasicBlock, StateType> blockEntryStatesCache = new IdentityHashMap<>();

  public IntraproceduralDataflowAnalysis(
      StateType bottom, AbstractTransferFunction<StateType> transfer) {
    this.bottom = bottom;
    this.transfer = transfer;
  }

  public DataflowAnalysisResult run(BasicBlock root) {
    return run(root, Timing.empty());
  }

  public DataflowAnalysisResult run(BasicBlock root, Timing timing) {
    return run(WorkList.newIdentityWorkList(root), timing);
  }

  private DataflowAnalysisResult run(WorkList<BasicBlock> worklist, Timing timing) {
    while (worklist.hasNext()) {
      BasicBlock initialBlock = worklist.next();
      BasicBlock block = initialBlock;
      BasicBlock end = null;
      // Compute the abstract state upon entry to the basic block, by joining all the predecessor
      // exit states.
      StateType state =
          timing.time("Compute block entry state", () -> computeBlockEntryState(initialBlock));

      timing.begin("Compute transfers");
      do {
        for (Instruction instruction : block.getInstructions()) {
          TransferFunctionResult<StateType> transferResult = transfer.apply(instruction, state);
          if (transferResult.isFailedTransferResult()) {
            timing.end();
            return new FailedDataflowAnalysisResult();
          }
          assert transferResult.isAbstractState();
          state = transferResult.asAbstractState();
        }
        if (block.hasUniqueSuccessorWithUniquePredecessor()) {
          block = block.getUniqueSuccessor();
        } else {
          end = block;
          block = null;
        }
      } while (block != null);
      timing.end();

      // Update the block exit state, and re-enqueue all successor blocks if the abstract state
      // changed.
      if (setBlockExitState(end, state)) {
        worklist.addAllIgnoringSeenSet(end.getSuccessors());
      }

      // Add the computed exit state to the entry state of each successor that satisfies the
      // predicate shouldCacheBlockEntryStateFor(successor).
      updateBlockEntryStateCacheForSuccessors(end, state);
    }
    return new SuccessfulDataflowAnalysisResult<>(blockExitStates);
  }

  private StateType computeBlockEntryState(BasicBlock block) {
    if (shouldCacheBlockEntryStateFor(block)) {
      return blockEntryStatesCache.getOrDefault(block, bottom);
    }
    StateType result = bottom;
    for (BasicBlock predecessor : block.getPredecessors()) {
      StateType edgeState =
          transfer.computeBlockEntryState(
              block, predecessor, blockExitStates.getOrDefault(predecessor, bottom));
      result = result.join(edgeState);
    }
    return result;
  }

  private boolean setBlockExitState(BasicBlock block, StateType state) {
    assert !block.hasUniqueSuccessorWithUniquePredecessor();
    StateType previous = blockExitStates.put(block, state);
    assert previous == null || state.isGreaterThanOrEquals(previous);
    return !state.equals(previous);
  }

  private void updateBlockEntryStateCacheForSuccessors(BasicBlock block, StateType state) {
    for (BasicBlock successor : block.getSuccessors()) {
      if (shouldCacheBlockEntryStateFor(successor)) {
        StateType edgeState = transfer.computeBlockEntryState(successor, block, state);
        StateType previous = blockEntryStatesCache.getOrDefault(successor, bottom);
        blockEntryStatesCache.put(successor, previous.join(edgeState));
      }
    }
  }

  private boolean shouldCacheBlockEntryStateFor(BasicBlock block) {
    return block.getPredecessors().size() > 2;
  }
}
