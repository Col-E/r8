// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.features;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.profile.startup.profile.StartupProfile;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;

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
      StartupProfile startupProfile,
      SyntheticItems syntheticItems) {
    return classToFeatureSplitMap.isInBaseOrSameFeatureAs(
        accessedClass, accessor, options, startupProfile, syntheticItems);
  }

  public static boolean isSafeForInlining(
      ProgramMethod caller,
      ProgramMethod callee,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    FeatureSplit callerFeatureSplit = classToFeatureSplitMap.getFeatureSplit(caller, appView);
    FeatureSplit calleeFeatureSplit = classToFeatureSplitMap.getFeatureSplit(callee, appView);

    // First guarantee that we don't cross any actual feature split boundaries.
    if (!calleeFeatureSplit.isBase()) {
      if (calleeFeatureSplit != callerFeatureSplit) {
        return false;
      }
    }

    // Next perform startup checks.
    if (!callee.getOptimizationInfo().forceInline()) {
      StartupProfile startupProfile = appView.getStartupProfile();
      OptionalBool callerIsStartupMethod = isStartupMethod(caller, startupProfile);
      if (callerIsStartupMethod.isTrue()) {
        // If the caller is a startup method, then only allow inlining if the callee is also a
        // startup
        // method.
        if (isStartupMethod(callee, startupProfile).isFalse()) {
          return false;
        }
      } else if (callerIsStartupMethod.isFalse()) {
        // If the caller is not a startup method, then only allow inlining if the caller is not a
        // startup class or the callee is a startup class.
        if (startupProfile.isStartupClass(caller.getHolderType())
            && !startupProfile.isStartupClass(callee.getHolderType())) {
          return false;
        }
      }
    }
    return true;
  }

  private static OptionalBool isStartupMethod(ProgramMethod method, StartupProfile startupProfile) {
    if (method.getDefinition().isD8R8Synthesized()) {
      // Due to inadequate rewriting of the startup list during desugaring, we do not give an
      // accurate result in this case.
      return OptionalBool.unknown();
    }
    return OptionalBool.of(startupProfile.containsMethodRule(method.getReference()));
  }

  public static boolean isSafeForVerticalClassMerging(
      DexProgramClass sourceClass,
      DexProgramClass targetClass,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    ClassToFeatureSplitMap classToFeatureSplitMap = appView.appInfo().getClassToFeatureSplitMap();
    FeatureSplit sourceFeatureSplit = classToFeatureSplitMap.getFeatureSplit(sourceClass, appView);
    FeatureSplit targetFeatureSplit = classToFeatureSplitMap.getFeatureSplit(targetClass, appView);

    // First guarantee that we don't cross any actual feature split boundaries.
    if (targetFeatureSplit.isBase()) {
      assert sourceFeatureSplit.isBase() : "Unexpected class in base that inherits from feature";
    } else {
      if (sourceFeatureSplit != targetFeatureSplit) {
        return false;
      }
    }

    // If the source class is a startup class then require that the target class is also a startup
    // class.
    StartupProfile startupProfile = appView.getStartupProfile();
    if (startupProfile.isStartupClass(sourceClass.getType())
        && !startupProfile.isStartupClass(targetClass.getType())) {
      return false;
    }
    return true;
  }
}
