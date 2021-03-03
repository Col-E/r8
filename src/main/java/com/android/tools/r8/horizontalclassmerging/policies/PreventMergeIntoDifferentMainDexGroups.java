// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.shaking.MainDexInfo.MainDexGroup;

public class PreventMergeIntoDifferentMainDexGroups
    extends MultiClassSameReferencePolicy<MainDexGroup> {

  private final MainDexInfo mainDexInfo;

  public PreventMergeIntoDifferentMainDexGroups(AppView<AppInfoWithLiveness> appView) {
    this.mainDexInfo = appView.appInfo().getMainDexInfo();
  }

  @Override
  public MainDexGroup getMergeKey(DexProgramClass clazz) {
    return mainDexInfo.canMerge(clazz)
        ? mainDexInfo.getMergeKey(clazz)
        : ineligibleForClassMerging();
  }
}
