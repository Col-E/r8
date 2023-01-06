// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

/**
 * A binding of a keep item.
 *
 * <p>A binding allows referencing the exact instance of a match from a condition in other
 * conditions and/or targets. It can also be used to reduce duplication of targets by sharing
 * patterns.
 *
 * <p>See KeepTarget for documentation on specifying an item pattern.
 */
public @interface KeepBinding {

  /** Name with which other bindings, conditions or targets can reference the bound item pattern. */
  String bindingName();

  String classFromBinding() default "";

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
