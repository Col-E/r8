// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.ir.code.Instruction;

/**
 * A transfer function that defines the abstract semantics of the instructions in the program
 * according to some abstract state {@link StateType}.
 */
public interface TransferFunction<StateType extends AbstractState<StateType>> {

  TransferFunctionResult<StateType> apply(Instruction instruction, StateType state);
}
