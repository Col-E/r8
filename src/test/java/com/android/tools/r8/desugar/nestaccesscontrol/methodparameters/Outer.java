// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol.methodparameters;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;

public class Outer {
  public static class Inner {
    @MyAnnotation
    private Inner() {
      for (Constructor<?> constructor : getClass().getDeclaredConstructors()) {
        for (Parameter parameter : constructor.getParameters()) {
          System.out.print(parameter.getType().getSimpleName());
          System.out.print(", ");
        }
        System.out.println(constructor.getParameters().length);
      }
    }

    @MyAnnotation
    private Inner(int a) {}

    @MyAnnotation
    private Inner(int a, int b) {}
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.CONSTRUCTOR)
  @interface MyAnnotation {}

  public static Inner callPrivateInnerConstructorZeroArgs() {
    return new Inner();
  }

  public static Inner callPrivateInnerConstructorOneArg() {
    return new Inner(0);
  }

  public static Inner callPrivateInnerConstructorTwoArgs() {
    return new Inner(0, 0);
  }
}
