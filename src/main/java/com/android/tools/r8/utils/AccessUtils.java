// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.SyntheticItems;

public class AccessUtils {

  public static boolean isAccessibleInSameContextsAs(
      DexType newType, DexType oldType, AppView<AppInfoWithLiveness> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexType newBaseType = newType.toBaseType(dexItemFactory);
    if (!newBaseType.isClassType()) {
      return true;
    }
    DexClass newBaseClass = appView.definitionFor(newBaseType);
    if (newBaseClass == null) {
      return false;
    }

    // If the new class is not public, then the old class must be non-public as well and reside in
    // the same package as the new class.
    DexType oldBaseType = oldType.toBaseType(dexItemFactory);
    if (!newBaseClass.isPublic()) {
      assert oldBaseType.isClassType();
      DexClass oldBaseClass = appView.definitionFor(oldBaseType);
      if (oldBaseClass == null
          || oldBaseClass.isPublic()
          || !newBaseType.isSamePackage(oldBaseType)) {
        return false;
      }
    }

    // If the new class is a program class, we need to check if it is in a feature.
    if (newBaseClass.isProgramClass()) {
      ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
      SyntheticItems syntheticItems = appView.getSyntheticItems();
      if (classToFeatureSplitMap != null) {
        FeatureSplit newFeatureSplit =
            classToFeatureSplitMap.getFeatureSplit(newBaseClass.asProgramClass(), syntheticItems);
        if (!newFeatureSplit.isBase()
            && newFeatureSplit
                != classToFeatureSplitMap.getFeatureSplit(oldBaseType, syntheticItems)) {
          return false;
        }
      }
    }
    return true;
  }
}
