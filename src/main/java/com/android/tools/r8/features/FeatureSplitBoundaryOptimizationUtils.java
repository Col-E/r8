// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.features;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.experimental.startup.StartupOrder;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;

public class FeatureSplitBoundaryOptimizationUtils {

  public static ConstraintWithTarget getInliningConstraintForResolvedMember(
      ProgramMethod method,
      DexEncodedMember<?, ?> resolvedMember,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    // We never inline into the base from a feature (calls should never happen) and we never inline
    // between features, so this check should be sufficient.
    if (classToFeatureSplitMap.isInBaseOrSameFeatureAs(
        resolvedMember.getHolderType(), method, appView)) {
      return ConstraintWithTarget.ALWAYS;
    }
    return ConstraintWithTarget.NEVER;
  }

  public static FeatureSplit getMergeKeyForHorizontalClassMerging(
      DexProgramClass clazz, AppView<? extends AppInfoWithClassHierarchy> appView) {
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    return classToFeatureSplitMap.getFeatureSplit(clazz, appView);
  }

  public static boolean isSafeForAccess(
      DexProgramClass accessedClass,
      ProgramDefinition accessor,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      InternalOptions options,
      StartupOrder startupOrder,
      SyntheticItems syntheticItems) {
    return classToFeatureSplitMap.isInBaseOrSameFeatureAs(
        accessedClass, accessor, options, startupOrder, syntheticItems);
  }

  public static boolean isSafeForInlining(
      ProgramMethod caller,
      ProgramMethod callee,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    if (classToFeatureSplitMap.isInSameFeatureOrBothInSameBase(callee, caller, appView)) {
      return true;
    }
    // Still allow inlining if we inline from the base into a feature.
    if (classToFeatureSplitMap.isInBase(callee.getHolder(), appView)) {
      return true;
    }
    return false;
  }

  public static boolean isSafeForVerticalClassMerging(
      DexProgramClass sourceClass,
      DexProgramClass targetClass,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    return classToFeatureSplitMap.isInSameFeatureOrBothInSameBase(
        sourceClass, targetClass, appView);
  }
}
