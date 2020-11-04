// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
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
    return isClassAccessible(clazz, context, appView.appInfo().getClassToFeatureSplitMap());
  }

  public static OptionalBool isClassAccessible(
      DexClass clazz, ProgramDefinition context, ClassToFeatureSplitMap classToFeatureSplitMap) {
    if (!clazz.isPublic() && !clazz.getType().isSamePackage(context.getContextType())) {
      return OptionalBool.FALSE;
    }
    if (clazz.isProgramClass()
        && !classToFeatureSplitMap.isInBaseOrSameFeatureAs(clazz.asProgramClass(), context)) {
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
        context,
        appInfo);
  }

  public static OptionalBool isMemberAccessible(
      DexClassAndMember<?, ?> member,
      DexClass initialResolutionHolder,
      ProgramDefinition context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isMemberAccessible(member, initialResolutionHolder, context, appView.appInfo());
  }

  public static OptionalBool isMemberAccessible(
      DexClassAndMember<?, ?> member,
      DexClass initialResolutionHolder,
      ProgramDefinition context,
      AppInfoWithClassHierarchy appInfo) {
    AccessFlags<?> memberFlags = member.getDefinition().getAccessFlags();
    OptionalBool classAccessibility =
        isClassAccessible(initialResolutionHolder, context, appInfo.getClassToFeatureSplitMap());
    if (classAccessibility.isFalse()) {
      return OptionalBool.FALSE;
    }
    if (memberFlags.isPublic()) {
      return classAccessibility;
    }
    if (memberFlags.isPrivate()) {
      if (!isNestMate(member.getHolder(), context.getContextClass())) {
        return OptionalBool.FALSE;
      }
      return classAccessibility;
    }
    if (member.getHolderType().isSamePackage(context.getContextType())) {
      return classAccessibility;
    }
    if (memberFlags.isProtected()
        && appInfo.isSubtype(context.getContextType(), member.getHolderType())) {
      return classAccessibility;
    }
    return OptionalBool.FALSE;
  }

  private static boolean isNestMate(DexClass clazz, DexProgramClass context) {
    if (clazz == context) {
      return true;
    }
    if (!clazz.isInANest() || !context.isInANest()) {
      return false;
    }
    return clazz.getNestHost() == context.getNestHost();
  }
}
