// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural.cf;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractState;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractTransferFunction;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.IntraProceduralDataflowAnalysisBase;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.IntraProceduralDataflowAnalysisOptions;

public class CfIntraproceduralDataflowAnalysis<StateType extends AbstractState<StateType>>
    extends IntraProceduralDataflowAnalysisBase<CfBlock, CfInstruction, StateType> {

  public CfIntraproceduralDataflowAnalysis(
      AppView<?> appView,
      StateType bottom,
      CfControlFlowGraph cfg,
      AbstractTransferFunction<CfBlock, CfInstruction, StateType> transfer) {
    super(
        appView,
        bottom,
        cfg,
        transfer,
        IntraProceduralDataflowAnalysisOptions.getCollapseInstance());
  }
}
