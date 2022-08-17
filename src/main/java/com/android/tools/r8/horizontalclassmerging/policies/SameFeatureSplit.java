// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.features.FeatureSplitBoundaryOptimizationUtils;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;

public class SameFeatureSplit extends MultiClassSameReferencePolicy<FeatureSplit> {
  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  public SameFeatureSplit(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
  }

  @Override
  public FeatureSplit getMergeKey(DexProgramClass clazz) {
    return FeatureSplitBoundaryOptimizationUtils.getMergeKeyForHorizontalClassMerging(
        clazz, appView);
  }

  @Override
  public String getName() {
    return "SameFeatureSplit";
  }
}
