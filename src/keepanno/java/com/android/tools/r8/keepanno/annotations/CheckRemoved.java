// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark that an item must be fully removed from the residual program.
 *
 * <p>Being removed from the program means that the item declaration is not present at all in the
 * residual program. For example, inlined functions are not considered removed. If content of the
 * item is allowed to be in the residual, use {@link CheckOptimizedOut}.
 *
 * <p>A class is removed if all of its members are removed and no references to the class remain.
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface CheckRemoved {

  String description() default "";
}
