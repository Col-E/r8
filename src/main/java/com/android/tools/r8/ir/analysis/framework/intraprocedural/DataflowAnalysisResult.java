// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

import com.android.tools.r8.ir.code.BasicBlock;

/**
 * The result returned by {@link IntraproceduralDataflowAnalysis#run(BasicBlock)}.
 *
 * <p>The result can be either a {@link SuccessfulDataflowAnalysisResult}, which represents the fact
 * that the dataflow analysis ran to completion and that the (least) fixpoint was computed, or a
 * {@link FailedDataflowAnalysisResult}, in which case the dataflow analysis was aborted before a
 * fixpoint was reached.
 *
 * <p>The result currently does not hold any data about the result of the analysis, but this would
 * be natural to add once needed.
 */
public abstract class DataflowAnalysisResult {

  public boolean isSuccessfulAnalysisResult() {
    return false;
  }

  public boolean isFailedAnalysisResult() {
    return false;
  }

  public static class SuccessfulDataflowAnalysisResult extends DataflowAnalysisResult {

    @Override
    public boolean isSuccessfulAnalysisResult() {
      return true;
    }
  }

  public static class FailedDataflowAnalysisResult extends DataflowAnalysisResult {

    @Override
    public boolean isFailedAnalysisResult() {
      return true;
    }
  }
}
