// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;

public class CheckSyntheticClasses extends SingleClassPolicy {

  private final HorizontalClassMergerOptions options;
  private final SyntheticItems syntheticItems;

  public CheckSyntheticClasses(AppView<?> appView) {
    this.options = appView.options().horizontalClassMergerOptions();
    this.syntheticItems = appView.getSyntheticItems();
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    if (!options.isSyntheticMergingEnabled() && syntheticItems.isSyntheticClass(clazz)) {
      return false;
    }
    if (options.isRestrictedToSynthetics()
        && !syntheticItems.isSyntheticClassEligibleForMerging(clazz)) {
      return false;
    }
    return true;
  }

  @Override
  public String getName() {
    return "CheckSyntheticClasses";
  }
}
