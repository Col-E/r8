// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b69825683.innerconstructsouter;

public class Outer {

  private Outer() {
  }

  public static class Inner {
    public Outer build() {
      return new Outer();
    }
  }

  public static void main(String args[]) {
    Inner builder = new Inner();
    builder.build();
    for (java.lang.reflect.Constructor m : Outer.class.getDeclaredConstructors()) {
      System.out.println(m);
    }
  }
}
