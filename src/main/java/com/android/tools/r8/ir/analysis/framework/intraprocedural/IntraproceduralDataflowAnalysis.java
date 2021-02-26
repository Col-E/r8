// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.FailedDataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.SuccessfulDataflowAnalysisResult;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.WorkList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * This defines a simple fixpoint solver for running an intraprocedural dataflow analysis.
 *
 * <p>The solver computes an {@link AbstractState} for each {@link BasicBlock} using the {@link
 * TransferFunction} which defines the abstract semantics for each instruction.
 *
 * <p>Once the fixpoint is reached the analysis returns a {@link SuccessfulDataflowAnalysisResult}.
 * If the supplied {@link TransferFunction} returns a {@link FailedTransferFunctionResult} for a
 * given instruction and abstract state, then the analysis return a {@link
 * FailedDataflowAnalysisResult}.
 */
public class IntraproceduralDataflowAnalysis<StateType extends AbstractState<StateType>> {

  private final StateType bottom;

  // The transfer function that defines the abstract semantics for each instruction.
  private final TransferFunction<StateType> transfer;

  // The state of the analysis.
  private final Map<BasicBlock, StateType> blockExitStates = new IdentityHashMap<>();

  public IntraproceduralDataflowAnalysis(StateType bottom, TransferFunction<StateType> transfer) {
    this.bottom = bottom;
    this.transfer = transfer;
  }

  public DataflowAnalysisResult run(BasicBlock root) {
    return run(WorkList.newIdentityWorkList(root));
  }

  private DataflowAnalysisResult run(WorkList<BasicBlock> worklist) {
    while (worklist.hasNext()) {
      BasicBlock block = worklist.next();
      BasicBlock end = null;
      // Compute the abstract state upon entry to the basic block, by joining all the predecessor
      // exit states.
      StateType state = computeBlockEntryState(block);
      do {
        for (Instruction instruction : block.getInstructions()) {
          TransferFunctionResult<StateType> transferResult = transfer.apply(instruction, state);
          if (transferResult.isFailedTransferResult()) {
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
      // Update the block exit state, and re-enqueue all successor blocks if the abstract state
      // changed.
      if (setBlockExitState(end, state)) {
        worklist.addAllIgnoringSeenSet(end.getSuccessors());
      }
    }
    return new SuccessfulDataflowAnalysisResult<>(blockExitStates);
  }

  private StateType computeBlockEntryState(BasicBlock block) {
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
}
