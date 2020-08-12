// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.errors.Unreachable;

/** Used by the {@link TransferFunction} to signal that the dataflow analysis should be aborted. */
public class FailedTransferFunctionResult<StateType extends AbstractState<StateType>>
    implements TransferFunctionResult<StateType> {

  public FailedTransferFunctionResult() {}

  @Override
  public StateType asAbstractState() {
    throw new Unreachable();
  }

  @Override
  public boolean isFailedTransferResult() {
    return true;
  }
}
