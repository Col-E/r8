// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A target for a keep edge.
 *
 * <p>The target denotes a keep item along with options for what to keep:
 *
 * <ul>
 *   <li>a class, or pattern on classes;
 *   <li>a method, or pattern on methods; or
 *   <li>a field, or pattern on fields.
 * </ul>
 *
 * <p>The structure of a target item is the same as for a condition item but has the additional keep
 * options.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface KeepTarget {

  // KeepTarget only content (keep options) =========

  KeepOption[] allow() default {};

  KeepOption[] disallow() default {};

  // Shared KeepItem content ========================

  String className() default "";

  Class<?> classConstant() default Object.class;

  String extendsClassName() default "";

  Class<?> extendsClassConstant() default Object.class;

  String methodName() default "";

  String methodReturnType() default "";

  String[] methodParameters() default {""};

  String fieldName() default "";

  String fieldType() default "";
}
