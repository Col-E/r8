// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.utils.OptionalBool;

/**
 * Definitions of access control routines.
 *
 * <p>Follows SE 11, jvm spec, section 5.4.4 on "Access Control", except for aspects related to
 * "run-time module", for which all items are assumed to be in the same single such module.
 */
public class AccessControl {

  public static OptionalBool isClassAccessible(DexClass clazz, DexProgramClass context) {
    if (clazz.accessFlags.isPublic()) {
      return OptionalBool.TRUE;
    }
    return OptionalBool.of(clazz.getType().isSamePackage(context.getType()));
  }

  public static OptionalBool isMethodAccessible(
      DexEncodedMethod method,
      DexClass holder,
      DexProgramClass context,
      AppInfoWithClassHierarchy appInfo) {
    return isMemberAccessible(method.accessFlags, holder, context, appInfo);
  }

  public static OptionalBool isFieldAccessible(
      DexEncodedField field,
      DexClass holder,
      DexProgramClass context,
      AppInfoWithClassHierarchy appInfo) {
    return isMemberAccessible(field.accessFlags, holder, context, appInfo);
  }

  private static OptionalBool isMemberAccessible(
      AccessFlags<?> memberFlags,
      DexClass holder,
      DexProgramClass context,
      AppInfoWithClassHierarchy appInfo) {
    OptionalBool classAccessibility = isClassAccessible(holder, context);
    if (classAccessibility.isFalse()) {
      return OptionalBool.FALSE;
    }
    if (memberFlags.isPublic()) {
      return classAccessibility;
    }
    if (memberFlags.isPrivate()) {
      if (!isNestMate(holder, context)) {
        return OptionalBool.FALSE;
      }
      return classAccessibility;
    }
    if (holder.getType().isSamePackage(context.getType())) {
      return classAccessibility;
    }
    if (memberFlags.isProtected() && appInfo.isSubtype(context.getType(), holder.getType())) {
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
