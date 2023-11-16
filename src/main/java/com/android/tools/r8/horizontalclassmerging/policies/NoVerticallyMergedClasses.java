// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class NoVerticallyMergedClasses extends SingleClassPolicy {
  private final AppView<AppInfoWithLiveness> appView;

  public NoVerticallyMergedClasses(AppView<AppInfoWithLiveness> appView, Mode mode) {
    // This policy is only relevant for the initial round, since all vertically merged classes have
    // been removed from the application in the final round of horizontal class merging.
    assert mode.isInitial();
    this.appView = appView;
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    if (appView.getVerticallyMergedClasses() == null) {
      return true;
    }
    return !appView.getVerticallyMergedClasses().hasBeenMergedIntoSubtype(program.type);
  }

  @Override
  public String getName() {
    return "NotVerticallyMergedIntoSubtype";
  }
}
