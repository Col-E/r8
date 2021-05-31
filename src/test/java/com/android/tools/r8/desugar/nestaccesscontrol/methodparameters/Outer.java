// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol.methodparameters;

import java.lang.reflect.Constructor;

public class Outer {
  public static class Inner {
    private Inner() {
      for (Constructor<?> constructor : getClass().getDeclaredConstructors()) {
        System.out.println(constructor.getParameters().length);
      }
    }

    private Inner(int a) {}

    private Inner(int a, int b) {}
  }

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
