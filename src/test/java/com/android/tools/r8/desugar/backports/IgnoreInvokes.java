// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Denote a method that contains invoke instructions on the target class which should be ignored
 * in the counts. This is useful for using other functionality of the target class to verify the
 * behavior of the backport.
 *
 * Methods with this annotation will never be inlined.
 */
@Target(METHOD)
@Retention(RUNTIME)
@interface IgnoreInvokes {
}
