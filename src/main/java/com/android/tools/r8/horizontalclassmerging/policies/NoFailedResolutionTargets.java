// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Set;

// TODO(b/192821424): Can be removed if handled.
public class NoFailedResolutionTargets extends SingleClassPolicy {

  private final Set<DexType> failedResolutionHolders;

  public NoFailedResolutionTargets(AppView<AppInfoWithLiveness> appView) {
    failedResolutionHolders = Sets.newIdentityHashSet();
    for (DexMethod method : appView.appInfo().getFailedMethodResolutionTargets()) {
      failedResolutionHolders.add(method.holder);
    }
  }

  @Override
  public String getName() {
    return "NoFailedResolutionTargets";
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    return !failedResolutionHolders.contains(program.getType());
  }
}
