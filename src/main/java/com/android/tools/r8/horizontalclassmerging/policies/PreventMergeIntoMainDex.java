// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.PreventMergeIntoMainDex.MainDexClassification;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.shaking.MainDexTracingResult;

public class PreventMergeIntoMainDex extends MultiClassSameReferencePolicy<MainDexClassification> {
  private final MainDexClasses mainDexClasses;
  private final MainDexTracingResult mainDexTracingResult;

  enum MainDexClassification {
    MAIN_DEX_LIST,
    MAIN_DEX_ROOT,
    MAIN_DEX_DEPENDENCY,
    NOT_IN_MAIN_DEX
  }

  public PreventMergeIntoMainDex(
      AppView<AppInfoWithLiveness> appView, MainDexTracingResult mainDexTracingResult) {
    this.mainDexClasses = appView.appInfo().getMainDexClasses();
    this.mainDexTracingResult = mainDexTracingResult;
  }

  @Override
  public MainDexClassification getMergeKey(DexProgramClass clazz) {
    if (mainDexClasses.contains(clazz)) {
      return MainDexClassification.MAIN_DEX_LIST;
    }
    if (mainDexTracingResult.isRoot(clazz)) {
      return MainDexClassification.MAIN_DEX_ROOT;
    }
    if (mainDexTracingResult.isDependency(clazz)) {
      return MainDexClassification.MAIN_DEX_DEPENDENCY;
    }
    return MainDexClassification.NOT_IN_MAIN_DEX;
  }
}
