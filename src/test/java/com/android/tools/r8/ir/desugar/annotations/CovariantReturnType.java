// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// This is a copy of dalvik.annotation.codegen.CovariantReturnType.
@Repeatable(CovariantReturnType.CovariantReturnTypes.class)
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface CovariantReturnType {
  Class<?> returnType();

  int presentAfter();

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.METHOD})
  @interface CovariantReturnTypes {
    CovariantReturnType[] value();
  }
}
