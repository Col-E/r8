// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.synthesis.SyntheticItems;

public class OnlySyntheticClasses extends SingleClassPolicy {

  private final SyntheticItems syntheticItems;

  public OnlySyntheticClasses(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.syntheticItems = appView.getSyntheticItems();
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    return syntheticItems.isSyntheticClass(clazz);
  }

  @Override
  public String getName() {
    return "OnlySyntheticClasses";
  }
}
