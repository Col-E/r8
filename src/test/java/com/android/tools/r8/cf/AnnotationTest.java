// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import java.lang.annotation.Annotation;

public class AnnotationTest {
  // @Deprecated is a runtime-visible annotation.
  @Deprecated public static boolean foo = true;

  @Deprecated
  public static boolean bar() {
    return true;
  }

  public static void main(String[] args) {
    try {
      testField();
      testMethod();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void testField() throws Exception {
    checkDeprecated("field 'foo'", AnnotationTest.class.getDeclaredField("foo").getAnnotations());
  }

  private static void testMethod() throws Exception {
    checkDeprecated("method 'bar'", AnnotationTest.class.getMethod("bar").getAnnotations());
  }

  private static void checkDeprecated(String what, Annotation[] annotations) {
    Annotation n;
    try {
      n = annotations[0];
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new RuntimeException(what + ": No annotations, expected @Deprecated", e);
    }
    if (!(n instanceof Deprecated)) {
      throw new RuntimeException(what + ": Expected @Deprecated, got: " + n);
    }
  }
}
