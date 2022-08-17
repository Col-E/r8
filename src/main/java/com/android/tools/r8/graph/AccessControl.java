// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.experimental.startup.StartupOrder;
import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.features.FeatureSplitBoundaryOptimizationUtils;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;

/**
 * Definitions of access control routines.
 *
 * <p>Follows SE 11, jvm spec, section 5.4.4 on "Access Control", except for aspects related to
 * "run-time module", for which all items are assumed to be in the same single such module.
 */
public class AccessControl {

  public static OptionalBool isClassAccessible(
      DexClass clazz,
      ProgramDefinition context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isClassAccessible(
        clazz,
        context,
        appView.appInfo().getClassToFeatureSplitMap(),
        appView.options(),
        appView.appInfo().getStartupOrder(),
        appView.getSyntheticItems());
  }

  public static OptionalBool isClassAccessible(
      DexClass clazz,
      Definition context,
      ClassToFeatureSplitMap classToFeatureSplitMap,
      InternalOptions options,
      StartupOrder startupOrder,
      SyntheticItems syntheticItems) {
    if (!clazz.isPublic() && !clazz.getType().isSamePackage(context.getContextType())) {
      return OptionalBool.FALSE;
    }
    if (clazz.isProgramClass()
        && context.isProgramDefinition()
        && !FeatureSplitBoundaryOptimizationUtils.isSafeForAccess(
            clazz.asProgramClass(),
            context.asProgramDefinition(),
            classToFeatureSplitMap,
            options,
            startupOrder,
            syntheticItems)) {
      return OptionalBool.UNKNOWN;
    }
    return OptionalBool.TRUE;
  }

  /** Intentionally package-private, use {@link MemberResolutionResult#isAccessibleFrom}. */
  static OptionalBool isMemberAccessible(
      SuccessfulMemberResolutionResult<?, ?> resolutionResult,
      ProgramDefinition context,
      AppInfoWithClassHierarchy appInfo) {
    return isMemberAccessible(
        resolutionResult.getResolutionPair(),
        resolutionResult.getInitialResolutionHolder(),
        context.getContextClass(),
        appInfo);
  }

  public static OptionalBool isMemberAccessible(
      DexClassAndMember<?, ?> member,
      DexClass initialResolutionHolder,
      ProgramDefinition context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isMemberAccessible(
        member, initialResolutionHolder, context.getContextClass(), appView.appInfo());
  }

  static OptionalBool isMemberAccessible(
      DexClassAndMember<?, ?> member,
      DexClass initialResolutionHolder,
      DexClass context,
      AppInfoWithClassHierarchy appInfo) {
    AccessFlags<?> memberFlags = member.getDefinition().getAccessFlags();
    OptionalBool classAccessibility =
        isClassAccessible(
            initialResolutionHolder,
            context,
            appInfo.getClassToFeatureSplitMap(),
            appInfo.options(),
            appInfo.getStartupOrder(),
            appInfo.getSyntheticItems());
    if (classAccessibility.isFalse()) {
      return OptionalBool.FALSE;
    }
    if (memberFlags.isPublic()) {
      return classAccessibility;
    }
    if (memberFlags.isPrivate()) {
      if (!isNestMate(member.getHolder(), context)) {
        return OptionalBool.FALSE;
      }
      return classAccessibility;
    }
    if (member.getHolderType().isSamePackage(context.getType())) {
      return classAccessibility;
    }
    if (memberFlags.isProtected() && appInfo.isSubtype(context.getType(), member.getHolderType())) {
      return classAccessibility;
    }
    return OptionalBool.FALSE;
  }

  private static boolean isNestMate(DexClass clazz, DexClass context) {
    if (clazz == context) {
      return true;
    }
    if (context == null) {
      assert false : "context should not be null";
      return false;
    }
    if (!clazz.isInANest() || !context.isInANest()) {
      return false;
    }
    return clazz.getNestHost() == context.getNestHost();
  }
}
