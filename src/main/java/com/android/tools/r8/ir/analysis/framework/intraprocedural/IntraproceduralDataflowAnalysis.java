// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;

public class IntraproceduralDataflowAnalysis<StateType extends AbstractState<StateType>>
    extends IntraProceduralDataflowAnalysisBase<BasicBlock, Instruction, StateType> {

  public IntraproceduralDataflowAnalysis(
      StateType bottom,
      IRCode code,
      AbstractTransferFunction<BasicBlock, Instruction, StateType> transfer) {
    super(bottom, code, transfer);
  }
}
