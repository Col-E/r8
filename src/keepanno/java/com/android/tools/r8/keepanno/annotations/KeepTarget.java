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
 *   <li>a pattern on classes;
 *   <li>a pattern on methods; or
 *   <li>a pattern on fields.
 * </ul>
 *
 * <p>The structure of a target item is the same as for a condition item but has the additional keep
 * options.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface KeepTarget {

  KeepItemKind kind() default KeepItemKind.DEFAULT;

  /**
   * Define the options that do not need to be preserved for the target.
   *
   * <p>Mutually exclusive with `disallow`.
   *
   * <p>If none are specified the default is "allow none" / "disallow all".
   */
  KeepOption[] allow() default {};

  /**
   * Define the options that *must* be preserved for the target.
   *
   * <p>Mutually exclusive with `allow`.
   *
   * <p>If none are specified the default is "allow none" / "disallow all".
   */
  KeepOption[] disallow() default {};

  /**
   * Define the class-name pattern by reference to a binding.
   *
   * <p>Mutually exclusive with `className` and `classConstant`.
   *
   * <p>If none are specified the default is to match any class.
   */
  String classFromBinding() default "";

  /**
   * Define the class-name pattern by fully qualified class name.
   *
   * <p>Mutually exclusive with `classFromBinding` and `classConstant`.
   *
   * <p>If none are specified the default is to match any class.
   */
  String className() default "";

  /**
   * Define the class-name pattern by reference to a Class constant.
   *
   * <p>Mutually exclusive with `classFromBinding` and `className`.
   *
   * <p>If none are specified the default is to match any class.
   */
  Class<?> classConstant() default Object.class;

  /**
   * Define the extends pattern by fully qualified class name.
   *
   * <p>Mutually exclusive with `extendsClassConstant`.
   *
   * <p>If none are specified the default is to match any extends clause.
   */
  String extendsClassName() default "";

  /**
   * Define the extends pattern by Class constant.
   *
   * <p>Mutually exclusive with `extendsClassName`.
   *
   * <p>If none are specified the default is to match any extends clause.
   */
  Class<?> extendsClassConstant() default Object.class;

  /**
   * Define the member pattern in full by a reference to a binding.
   *
   * <p>Mutually exclusive with all other pattern properties. When a member binding is referenced
   * this item is defined to be that item, including its class and member patterns.
   */
  String memberFromBinding() default "";

  /**
   * Define the member pattern by matching on access flags.
   *
   * <p>Mutually exclusive with all field and method patterns as use restricts the match to both
   * types of members.
   */
  MemberAccessFlags[] memberAccess() default {};

  /**
   * Define the method pattern by matching on access flags.
   *
   * <p>Mutually exclusive with any field properties.
   */
  MethodAccessFlags[] methodAccess() default {};

  /**
   * Define the method-name pattern by an exact method name.
   *
   * <p>Mutually exclusive with any field properties.
   *
   * <p>If none and other properties define this as a method the default matches any method name.
   */
  String methodName() default "";

  /**
   * Define the method return-type pattern by a fully qualified type or 'void'.
   *
   * <p>Mutually exclusive with any field properties.
   *
   * <p>If none and other properties define this as a method the default matches any return type.
   */
  String methodReturnType() default "";

  /**
   * Define the method parameters pattern by a list of fully qualified types.
   *
   * <p>Mutually exclusive with any field properties.
   *
   * <p>If none and other properties define this as a method the default matches any parameters.
   */
  String[] methodParameters() default {""};

  /**
   * Define the field pattern by matching on field access flags.
   *
   * <p>Mutually exclusive with any method properties.
   */
  FieldAccessFlags[] fieldAccess() default {};

  /**
   * Define the field-name pattern by an exact field name.
   *
   * <p>Mutually exclusive with any method properties.
   *
   * <p>If none and other properties define this as a field the default matches any field name.
   */
  String fieldName() default "";

  /**
   * Define the field-type pattern by a fully qualified type.
   *
   * <p>Mutually exclusive with any method properties.
   *
   * <p>If none and other properties define this as a field the default matches any field type.
   */
  String fieldType() default "";
}
