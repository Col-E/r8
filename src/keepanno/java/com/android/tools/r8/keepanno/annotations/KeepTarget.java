// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// ***********************************************************************************
// GENERATED FILE. DO NOT EDIT! See KeepItemAnnotationGenerator.java.
// ***********************************************************************************

package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A target for a keep edge.
 *
 * <p>The target denotes an item along with options for what to keep. An item can be:
 *
 * <ul>
 *   <li>a pattern on classes;
 *   <li>a pattern on methods; or
 *   <li>a pattern on fields.
 * </ul>
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface KeepTarget {

  /**
   * Specify the kind of this item pattern.
   *
   * <p>Possible values are:
   *
   * <ul>
   *   <li>ONLY_CLASS
   *   <li>ONLY_MEMBERS
   *   <li>CLASS_AND_MEMBERS
   * </ul>
   *
   * <p>If unspecified the default for an item with no member patterns is ONLY_CLASS and if it does
   * have member patterns the default is ONLY_MEMBERS
   *
   * @return The kind for this pattern.
   */
  KeepItemKind kind() default KeepItemKind.DEFAULT;

  /**
   * Define the options that are allowed to be modified.
   *
   * <p>The specified options do not need to be preserved for the target.
   *
   * <p>Mutually exclusive with the property `disallow` also defining options.
   *
   * <p>If nothing is specified for options the default is "allow none" / "disallow all".
   *
   * @return Options allowed to be modified for the target.
   */
  KeepOption[] allow() default {};

  /**
   * Define the options that are not allowed to be modified.
   *
   * <p>The specified options *must* be preserved for the target.
   *
   * <p>Mutually exclusive with the property `allow` also defining options.
   *
   * <p>If nothing is specified for options the default is "allow none" / "disallow all".
   *
   * @return Options not allowed to be modified for the target.
   */
  KeepOption[] disallow() default {};

  /**
   * Define the class pattern by reference to a binding.
   *
   * <p>Mutually exclusive with the following other properties defining class:
   *
   * <ul>
   *   <li>className
   *   <li>classConstant
   *   <li>instanceOfClassName
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstant
   *   <li>instanceOfClassConstantExclusive
   *   <li>extendsClassName
   *   <li>extendsClassConstant
   * </ul>
   *
   * <p>If none are specified the default is to match any class.
   *
   * @return The name of the binding that defines the class.
   */
  String classFromBinding() default "";

  /**
   * Define the class-name pattern by fully qualified class name.
   *
   * <p>Mutually exclusive with the following other properties defining class-name:
   *
   * <ul>
   *   <li>classConstant
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class name.
   *
   * @return The qualified class name that defines the class.
   */
  String className() default "";

  /**
   * Define the class-name pattern by reference to a Class constant.
   *
   * <p>Mutually exclusive with the following other properties defining class-name:
   *
   * <ul>
   *   <li>className
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class name.
   *
   * @return The class-constant that defines the class.
   */
  Class<?> classConstant() default Object.class;

  /**
   * Define the instance-of pattern as classes that are instances of the fully qualified class name.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstant
   *   <li>instanceOfClassConstantExclusive
   *   <li>extendsClassName
   *   <li>extendsClassConstant
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The qualified class name that defines what instance-of the class must be.
   */
  String instanceOfClassName() default "";

  /**
   * Define the instance-of pattern as classes that are instances of the fully qualified class name.
   *
   * <p>The pattern is exclusive in that it does not match classes that are instances of the
   * pattern, but only those that are instances of classes that are subclasses of the pattern.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassName
   *   <li>instanceOfClassConstant
   *   <li>instanceOfClassConstantExclusive
   *   <li>extendsClassName
   *   <li>extendsClassConstant
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The qualified class name that defines what instance-of the class must be.
   */
  String instanceOfClassNameExclusive() default "";

  /**
   * Define the instance-of pattern as classes that are instances the referenced Class constant.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassName
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstantExclusive
   *   <li>extendsClassName
   *   <li>extendsClassConstant
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The class constant that defines what instance-of the class must be.
   */
  Class<?> instanceOfClassConstant() default Object.class;

  /**
   * Define the instance-of pattern as classes that are instances the referenced Class constant.
   *
   * <p>The pattern is exclusive in that it does not match classes that are instances of the
   * pattern, but only those that are instances of classes that are subclasses of the pattern.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassName
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstant
   *   <li>extendsClassName
   *   <li>extendsClassConstant
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The class constant that defines what instance-of the class must be.
   */
  Class<?> instanceOfClassConstantExclusive() default Object.class;

  /**
   * Define the instance-of pattern as classes extending the fully qualified class name.
   *
   * <p>The pattern is exclusive in that it does not match classes that are instances of the
   * pattern, but only those that are instances of classes that are subclasses of the pattern.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassName
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstant
   *   <li>instanceOfClassConstantExclusive
   *   <li>extendsClassConstant
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The class name that defines what the class must extend.
   * @deprecated This property is deprecated, use {@link #instanceOfClassName} instead.
   */
  @Deprecated
  String extendsClassName() default "";

  /**
   * Define the instance-of pattern as classes extending the referenced Class constant.
   *
   * <p>The pattern is exclusive in that it does not match classes that are instances of the
   * pattern, but only those that are instances of classes that are subclasses of the pattern.
   *
   * <p>Mutually exclusive with the following other properties defining instance-of:
   *
   * <ul>
   *   <li>instanceOfClassName
   *   <li>instanceOfClassNameExclusive
   *   <li>instanceOfClassConstant
   *   <li>instanceOfClassConstantExclusive
   *   <li>extendsClassName
   *   <li>classFromBinding
   * </ul>
   *
   * <p>If none are specified the default is to match any class instance.
   *
   * @return The class constant that defines what the class must extend.
   * @deprecated This property is deprecated, use {@link #instanceOfClassConstant} instead.
   */
  @Deprecated
  Class<?> extendsClassConstant() default Object.class;

  /**
   * Define the member pattern in full by a reference to a binding.
   *
   * <p>Mutually exclusive with all other class and member pattern properties. When a member binding
   * is referenced this item is defined to be that item, including its class and member patterns.
   *
   * @return The binding name that defines the member.
   */
  String memberFromBinding() default "";

  /**
   * Define the member-access pattern by matching on access flags.
   *
   * <p>Mutually exclusive with all field and method properties as use restricts the match to both
   * types of members.
   *
   * @return The access flags constraints that must be met.
   */
  MemberAccessFlags[] memberAccess() default {};

  /**
   * Define the method-access pattern by matching on access flags.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any
   * method-access flags.
   *
   * @return The access flags constraints that must be met.
   */
  MethodAccessFlags[] methodAccess() default {};

  /**
   * Define the method-name pattern by an exact method name.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any method
   * name.
   *
   * @return The exact method name of the method.
   */
  String methodName() default "";

  /**
   * Define the method return-type pattern by a fully qualified type or 'void'.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any return
   * type.
   *
   * @return The qualified type name of the method return type.
   */
  String methodReturnType() default "";

  /**
   * Define the method parameters pattern by a list of fully qualified types.
   *
   * <p>Mutually exclusive with all field properties.
   *
   * <p>If none, and other properties define this item as a method, the default matches any
   * parameters.
   *
   * @return The list of qualified type names of the method parameters.
   */
  String[] methodParameters() default {"<default>"};

  /**
   * Define the field-access pattern by matching on access flags.
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none, and other properties define this item as a field, the default matches any
   * field-access flags.
   *
   * @return The access flags constraints that must be met.
   */
  FieldAccessFlags[] fieldAccess() default {};

  /**
   * Define the field-name pattern by an exact field name.
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none, and other properties define this item as a field, the default matches any field
   * name.
   *
   * @return The exact field name of the field.
   */
  String fieldName() default "";

  /**
   * Define the field-type pattern by a fully qualified type.
   *
   * <p>Mutually exclusive with all method properties.
   *
   * <p>If none, and other properties define this item as a field, the default matches any type.
   *
   * @return The qualified type name of the field type.
   */
  String fieldType() default "";
}
