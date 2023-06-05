// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class, field or method as being reflectively accessed.
 *
 * <p>Note: Before using this annotation, consider if instead you can annotate the code that is
 * doing reflection with {@link UsesReflection}. Annotating the reflecting code is generally more
 * clear and maintainable, and it also naturally gives rise to edges that describe just the
 * reflected aspects of the program. The {@link UsedByReflection} annotation is suitable for cases
 * where the reflecting code is not under user control, or in migrating away from rules.
 *
 * <p>When a class is annotated, member patterns can be used to define which members are to be kept.
 * When no member patterns are specified the default pattern is to match just the class.
 *
 * <p>When a member is annotated, the member patterns cannot be used as the annotated member itself
 * fully defines the item to be kept (i.e., itself).
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface UsedByReflection {
  String description() default "";

  /**
   * Conditions that should be satisfied for the annotation to be in effect.
   *
   * <p>Defaults to no conditions, thus trivially/unconditionally satisfied.
   */
  KeepCondition[] preconditions() default {};

  /** Additional targets to be kept in addition to the annotated class/members. */
  KeepTarget[] additionalTargets() default {};

  /**
   * The target kind to be kept.
   *
   * <p>When annotating a class without member patterns, the default kind is {@link
   * KeepItemKind#ONLY_CLASS}.
   *
   * <p>When annotating a class with member patterns, the default kind is {@link
   * KeepItemKind#CLASS_AND_MEMBERS}.
   *
   * <p>When annotating a member, the default kind is {@link KeepItemKind#ONLY_MEMBERS}.
   *
   * <p>It is not possible to use ONLY_CLASS if annotating a member.
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
