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
 * Annotation to declare the reflective usages made by a class, method or field.
 *
 * <p>The annotation's 'value' is a list of targets to be kept if the annotated item is used. The
 * annotated item is a precondition for keeping any of the specified targets. Thus, if an annotated
 * method is determined to be unused by the program, the annotation itself will not be in effect and
 * the targets will not be kept (assuming nothing else is otherwise keeping them).
 *
 * <p>The annotation's 'additionalPreconditions' is optional and can specify additional conditions
 * that should be satisfied for the annotation to be in effect.
 *
 * <p>The translation of the {@link UsesReflection} annotation into a {@link KeepEdge} is as
 * follows:
 *
 * <p>Assume the item of the annotation is denoted by 'CTX' and referred to as its context.
 *
 * <pre>
 * &#64;UsesReflection(value = targets, [additionalPreconditions = preconditions])
 * ==&gt;
 * &#64;KeepEdge(
 *   consequences = targets,
 *   preconditions = {createConditionFromContext(CTX)} + preconditions
 * )
 *
 * where
 *   KeepCondition createConditionFromContext(ctx) {
 *     if (ctx.isClass()) {
 *       return new KeepCondition(classTypeName = ctx.getClassTypeName());
 *     }
 *     if (ctx.isMethod()) {
 *       return new KeepCondition(
 *         classTypeName = ctx.getClassTypeName(),
 *         methodName = ctx.getMethodName(),
 *         methodReturnType = ctx.getMethodReturnType(),
 *         methodParameterTypes = ctx.getMethodParameterTypes());
 *     }
 *     if (ctx.isField()) {
 *       return new KeepCondition(
 *         classTypeName = ctx.getClassTypeName(),
 *         fieldName = ctx.getFieldName()
 *         fieldType = ctx.getFieldType());
 *     }
 *     // unreachable
 *   }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface UsesReflection {

  /**
   * Optional description to document the reason for this annotation.
   *
   * @return The descriptive message. Defaults to no description.
   */
  String description() default "";

  /**
   * Consequences that must be kept if the annotation is in effect.
   *
   * @return The list of target consequences.
   */
  KeepTarget[] value();

  /**
   * Additional preconditions for the annotation to be in effect.
   *
   * @return The list of additional preconditions. Defaults to no additional preconditions.
   */
  KeepCondition[] additionalPreconditions() default {};
}
