// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that an item must be optimized out of the residual program.
 *
 * <p>An item is optimized out if its declaration is no longer present in the residual program. This
 * can happen due to many distinct optimizations. For example, it may be dead code and removed by
 * the usual shrinking. The item's declaration may also be removed if it could be inlined at all
 * usages, in which case the output may still contain a value if it was a field, or instructions if
 * it was a method.
 *
 * <p>CAUTION: Because of the dependency on shrinker internal optimizations and details such as
 * inlining vs merging, the use of this annotation is somewhat unreliable and should be used with
 * caution. In most cases it is more appropriate to use {@link CheckRemoved}.
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface CheckOptimizedOut {

  String description() default "";
}
