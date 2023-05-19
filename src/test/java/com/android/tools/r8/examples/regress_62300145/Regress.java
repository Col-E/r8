// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.regress_62300145;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;

public class Regress {
  @Retention(RetentionPolicy.RUNTIME)
  public @interface A {
  }

  @Retention(CLASS)
  @Target({ElementType.PARAMETER})
  public @interface B {
  }

  public class InnerClass {
    public InnerClass(@A @B String p1, @A String p2, @B String p3) { }
  }

  public static void main(String[] args) throws NoSuchMethodException {
    Constructor<InnerClass> constructor = InnerClass.class.getDeclaredConstructor(
        Regress.class, String.class, String.class, String.class);
    Annotation[][] annotations = constructor.getParameterAnnotations();
    int index = 0;
    for (int i = 0; i < annotations.length; i++) {
      // On 9 and below, annotations are also printed for the Regress.class (first argument to the
      // getDeclaredConstructor). b/120402200
      if (i != 0 || annotations[i].length != 0) {
        System.out.print(index++ + ": ");
      }
      for (Annotation annotation : annotations[i]) {
        System.out.println(annotation);
      }
    }
  }
}
