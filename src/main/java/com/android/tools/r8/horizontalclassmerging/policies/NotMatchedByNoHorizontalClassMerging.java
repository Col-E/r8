// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class NotMatchedByNoHorizontalClassMerging extends SingleClassPolicy {

  private final AppView<AppInfoWithLiveness> appView;

  public NotMatchedByNoHorizontalClassMerging(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  @Override
  public boolean canMerge(DexProgramClass clazz) {
    return !appView.appInfo().isNoHorizontalClassMergingOfType(clazz);
  }

  @Override
  public String getName() {
    return "NotMatchedByNoHorizontalClassMerging";
  }
}
