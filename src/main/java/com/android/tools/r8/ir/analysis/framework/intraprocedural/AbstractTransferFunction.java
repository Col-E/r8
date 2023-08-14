// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.FailedDataflowAnalysisResult;

/**
 * A transfer function that defines the abstract semantics of the instructions in the program
 * according to some abstract state {@link StateType}.
 */
public interface AbstractTransferFunction<
    Block, Instruction, StateType extends AbstractState<StateType>> {

  /** Applies the effect of the given instruction on the given abstract state. */
  TransferFunctionResult<StateType> apply(Instruction instruction, StateType state);

  default TransferFunctionResult<StateType> applyBlock(Block block, StateType state) {
    return state;
  }

  /**
   * Computes the analysis state for the method entry point, i.e., the state prior to the first
   * instruction.
   */
  default StateType computeInitialState(Block entryBlock, StateType bottom) {
    return bottom;
  }

  /** Transfers the state from predecessor block to its successor block. */
  default StateType computeBlockEntryState(
      Block block, Block predecessor, StateType predecessorExitState) {
    return predecessorExitState;
  }

  /**
   * Returns true if (a function of) the abstract state at the given (throwing) instruction should
   * be transferred to the active catch handlers.
   */
  default boolean shouldTransferExceptionalControlFlowFromInstruction(
      Block throwBlock, Instruction throwInstruction) {
    return true;
  }

  /**
   * Transfers the state from the given (throwing) instruction to its catch handler.
   *
   * <p>Only called if {@link #shouldTransferExceptionalControlFlowFromInstruction} has returned
   * true.
   */
  default StateType computeExceptionalBlockEntryState(
      Block block,
      DexType guard,
      Block throwBlock,
      Instruction throwInstruction,
      StateType throwState) {
    return throwState;
  }

  default FailedDataflowAnalysisResult createFailedAnalysisResult(
      Instruction instruction, TransferFunctionResult<StateType> transferResult) {
    return new FailedDataflowAnalysisResult();
  }
}
