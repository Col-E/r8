// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declare the reflective usages made by an item.
 *
 * <p>The annotation 'value' is a list of targets which are to be kept if the annotated item is
 * kept. The structure of 'value' is identical to the 'consequences' field of a @KeepEdge
 * annotation.
 *
 * <p>The translation of the @UsesReflection annotation into a @KeepEdge is as follows:
 *
 * <p>Assume the item of the annotation is denoted by 'CTX' which and referred to as its context.
 *
 * <pre>
 * @UsesReflection(targets)
 * ==>
 * @KeepEdge(
 *   consequences = targets,
 *   preconditions = {createConditionFromContext(CTX)}
 * )
 *
 * where
 *   KeepCondition createConditionFromContext(ctx) {
 *     if (ctx.isClass()) {
 *       return KeepItem(classTypeName = ctx.getClassTypeName());
 *     }
 *     if (ctx.isMethod()) {
 *       return KeepCondition(
 *         classTypeName = ctx.getClassTypeName(),
 *         methodName = ctx.getMethodName(),
 *         methodReturnType = ctx.getMethodReturnType(),
 *         methodParameterTypes = ctx.getMethodParameterTypes());
 *     }
 *     if (ctx.isField()) {
 *       return KeepCondition(
 *         classTypeName = ctx.getClassTypeName(),
 *         fieldName = ctx.getFieldName()
 *         fieldType = ctx.getFieldType());
 *     }
 *     // unreachable
 *   }
 * </pre>
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface UsesReflection {
  KeepTarget[] value();
}
