// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;

public class IntraproceduralDataflowAnalysis<StateType extends AbstractState<StateType>>
    extends IntraProceduralDataflowAnalysisBase<BasicBlock, Instruction, StateType> {

  public IntraproceduralDataflowAnalysis(
      AppView<?> appView,
      StateType bottom,
      IRCode code,
      AbstractTransferFunction<BasicBlock, Instruction, StateType> transfer) {
    this(
        appView,
        bottom,
        code,
        transfer,
        IntraProceduralDataflowAnalysisOptions.getCollapseInstance());
  }

  public IntraproceduralDataflowAnalysis(
      AppView<?> appView,
      StateType bottom,
      IRCode code,
      AbstractTransferFunction<BasicBlock, Instruction, StateType> transfer,
      IntraProceduralDataflowAnalysisOptions options) {
    super(appView, bottom, code, transfer, options);
  }

  @Override
  boolean shouldCacheBlockEntryStateForNormalBlock(BasicBlock block) {
    return block.getPredecessors().size() > 2;
  }
}
