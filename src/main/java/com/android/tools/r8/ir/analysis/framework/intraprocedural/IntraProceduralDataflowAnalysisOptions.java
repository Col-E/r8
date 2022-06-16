// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.framework.intraprocedural;

public class IntraProceduralDataflowAnalysisOptions {

  private static final IntraProceduralDataflowAnalysisOptions COLLAPSE_INSTANCE =
      new IntraProceduralDataflowAnalysisOptions(true);
  private static final IntraProceduralDataflowAnalysisOptions NO_COLLAPSE_INSTANCE =
      new IntraProceduralDataflowAnalysisOptions(false);

  private final boolean isCollapsingOfTrivialEdgesEnabled;

  IntraProceduralDataflowAnalysisOptions(boolean isCollapsingOfTrivialEdgesEnabled) {
    this.isCollapsingOfTrivialEdgesEnabled = isCollapsingOfTrivialEdgesEnabled;
  }

  public boolean isCollapsingOfTrivialEdgesEnabled() {
    return isCollapsingOfTrivialEdgesEnabled;
  }

  public static IntraProceduralDataflowAnalysisOptions getCollapseInstance() {
    return COLLAPSE_INSTANCE;
  }

  public static IntraProceduralDataflowAnalysisOptions getNoCollapseInstance() {
    return NO_COLLAPSE_INSTANCE;
  }
}
