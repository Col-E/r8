// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.ir.code.BasicBlock;

/** The abstract state of the dataflow analysis, which is computed for each {@link BasicBlock}. */
public abstract class AbstractState<StateType extends AbstractState<StateType>>
    implements TransferFunctionResult<StateType> {

  public abstract StateType join(StateType state);

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();

  @Override
  public boolean isAbstractState() {
    return true;
  }
}
