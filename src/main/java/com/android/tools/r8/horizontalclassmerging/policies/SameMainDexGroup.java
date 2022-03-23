// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.shaking.MainDexInfo.MainDexGroup;
import com.android.tools.r8.synthesis.SyntheticItems;

public class SameMainDexGroup extends MultiClassSameReferencePolicy<MainDexGroup> {

  private final MainDexInfo mainDexInfo;
  private final SyntheticItems synthetics;

  public SameMainDexGroup(AppView<?> appView) {
    mainDexInfo = appView.appInfo().getMainDexInfo();
    synthetics = appView.getSyntheticItems();
  }

  @Override
  public MainDexGroup getMergeKey(DexProgramClass clazz) {
    return mainDexInfo.canMerge(clazz, synthetics)
        ? mainDexInfo.getMergeKey(clazz, synthetics)
        : ineligibleForClassMerging();
  }

  @Override
  public String getName() {
    return "SameMainDexGroup";
  }
}
