// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A condition for a keep edge.
 *
 * <p>The condition denotes a keep item:
 *
 * <ul>
 *   <li>a class, or pattern on classes;
 *   <li>a method, or pattern on methods; or
 *   <li>a field, or pattern on fields.
 * </ul>
 *
 * <p>The structure of a condition item is the same as for a target item but without a notion of
 * "keep options".
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface KeepCondition {
  Class<?> classConstant() default Object.class;

  String classTypeName() default "";

  String methodName() default "";

  String fieldName() default "";
}
