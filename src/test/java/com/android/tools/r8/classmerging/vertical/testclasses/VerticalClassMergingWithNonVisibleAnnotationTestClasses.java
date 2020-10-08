// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical.testclasses;

import com.android.tools.r8.NeverInline;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class VerticalClassMergingWithNonVisibleAnnotationTestClasses {

  @Retention(RetentionPolicy.RUNTIME)
  private @interface PrivateClassAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  private @interface PrivateMethodAnnotation {
    Class<?> value();
  }

  private static class PrivateType {}

  @PrivateClassAnnotation
  public abstract static class Base {

    @PrivateMethodAnnotation(PrivateType.class)
    @NeverInline
    public void foo() {
      System.out.println("Base::foo()");
    }

    public abstract void bar();
  }
}
