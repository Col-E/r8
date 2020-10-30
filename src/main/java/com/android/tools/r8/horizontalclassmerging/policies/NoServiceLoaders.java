// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

public class NoServiceLoaders extends SingleClassPolicy {
  private final AppView<AppInfoWithLiveness> appView;
  private final Set<DexType> allServiceImplementations;

  public NoServiceLoaders(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;

    allServiceImplementations = appView.appServices().computeAllServiceImplementations();
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !appView.appServices().allServiceTypes().contains(program.getType())
        && !allServiceImplementations.contains(program.getType());
  }
}
