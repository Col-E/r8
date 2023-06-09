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
 * <p>See KeepTarget for documentation on specifying an item pattern.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface KeepCondition {

  String classFromBinding() default "";

  String className() default "";

  Class<?> classConstant() default Object.class;

  String extendsClassName() default "";

  Class<?> extendsClassConstant() default Object.class;

  String memberFromBinding() default "";

  String methodName() default "";

  String methodReturnType() default "";

  String[] methodParameters() default {""};

  String fieldName() default "";

  String fieldType() default "";
}
