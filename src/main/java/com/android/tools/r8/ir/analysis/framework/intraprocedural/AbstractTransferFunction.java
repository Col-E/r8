// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

/**
 * A transfer function that defines the abstract semantics of the instructions in the program
 * according to some abstract state {@link StateType}.
 */
public interface AbstractTransferFunction<
    Block, Instruction, StateType extends AbstractState<StateType>> {

  TransferFunctionResult<StateType> apply(Instruction instruction, StateType state);

  default StateType computeInitialState(Block entryBlock, StateType bottom) {
    return bottom;
  }

  default StateType computeBlockEntryState(
      Block block, Block predecessor, StateType predecessorExitState) {
    return predecessorExitState;
  }
}
