// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.ir.code.Instruction;

/**
 * The result of applying the {@link TransferFunction} to an {@link Instruction} and an {@link
 * AbstractState}.
 *
 * <p>The result can either be a new {@link AbstractState} or a failure, in which case the dataflow
 * analysis is aborted.
 */
public interface TransferFunctionResult<StateType extends AbstractState<StateType>> {

  default boolean isAbstractState() {
    return false;
  }

  StateType asAbstractState();

  default boolean isFailedTransferResult() {
    return false;
  }

  default FailedTransferFunctionResult<StateType> asFailedTransferResult() {
    return null;
  }
}
