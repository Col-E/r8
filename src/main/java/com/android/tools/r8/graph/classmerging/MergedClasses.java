// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public interface MergedClasses {

  boolean verifyAllSourcesPruned(AppView<AppInfoWithLiveness> appView);

  boolean hasBeenMerged(DexType type);

  /**
   * Determine if the class has been merged by the merged classes object. If the merged classes is
   * null then return false.
   */
  static boolean hasBeenMerged(MergedClasses mergedClasses, DexProgramClass clazz) {
    return hasBeenMerged(mergedClasses, clazz.type);
  }

  /**
   * Determine if the class has been merged by the merged classes object. If the merged classes is
   * null then return false.
   */
  static boolean hasBeenMerged(MergedClasses mergedClasses, DexType type) {
    if (mergedClasses != null) {
      return mergedClasses.hasBeenMerged(type);
    }
    return false;
  }
}
