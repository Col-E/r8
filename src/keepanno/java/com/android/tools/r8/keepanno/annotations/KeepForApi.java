// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class, field or method as part of a library API surface.
 *
 * <p>When a class is annotated, member patterns can be used to define which members are to be kept.
 * When no member patterns are specified the default pattern matches all public and protected
 * members.
 *
 * <p>When a member is annotated, the member patterns cannot be used as the annotated member itself
 * fully defines the item to be kept (i.e., itself).
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface KeepForApi {
  String description() default "";

  /** Additional targets to be kept as part of the API surface. */
  KeepTarget[] additionalTargets() default {};

  /**
   * The target kind to be kept.
   *
   * <p>Default kind is CLASS_AND_MEMBERS, meaning the annotated class and/or member is to be kept.
   * When annotating a class this can be set to ONLY_CLASS to avoid patterns on any members. That
   * can be useful when the API members are themselves explicitly annotated.
   *
   * <p>It is not possible to use ONLY_CLASS if annotating a member. Also, it is never valid to use
   * kind ONLY_MEMBERS as the API surface must keep the class if any member it to be accessible.
   */
  KeepItemKind kind() default KeepItemKind.DEFAULT;

  // Member patterns. See KeepTarget for documentation.
  MemberAccessFlags[] memberAccess() default {};

  MethodAccessFlags[] methodAccess() default {};

  String methodName() default "";

  String methodReturnType() default "";

  String[] methodParameters() default {""};

  FieldAccessFlags[] fieldAccess() default {};

  String fieldName() default "";

  String fieldType() default "";
}
