// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClasses;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Map;

public class HorizontallyMergedClasses implements MergedClasses {
  private final Map<DexType, DexType> horizontallyMergedClasses;

  public HorizontallyMergedClasses(Map<DexType, DexType> horizontallyMergedClasses) {
    this.horizontallyMergedClasses = horizontallyMergedClasses;
  }

  @Override
  public boolean verifyAllSourcesPruned(AppView<AppInfoWithLiveness> appView) {
    for (DexType source : horizontallyMergedClasses.keySet()) {
      assert appView.appInfo().wasPruned(source)
          : "Expected horizontally merged lambda class `"
              + source.toSourceString()
              + "` to be absent";
    }
    return true;
  }

  @Override
  public boolean hasBeenMerged(DexType type) {
    return horizontallyMergedClasses.containsKey(type);
  }
}
