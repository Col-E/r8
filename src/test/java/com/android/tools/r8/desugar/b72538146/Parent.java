// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.b72538146;

import java.util.function.Supplier;

public class Parent {
  private static final Inner1 INNER_1;
  static {
    Supplier<Inner1> inner1Supplier = Inner1::new;
    INNER_1 = inner1Supplier.get();
  }

  private static final Supplier<Inner2> INNER_2_SUPPLIER = Inner2::new;

  private static final Inner3 INNER_3;
  static {
    Supplier<Inner3> inner3Supplier = Inner3::new;
    INNER_3 = inner3Supplier.get();
  }

  public Parent() {
  }

  public static class Inner1 {

    public Inner1() {
    }
  }

  public static class Inner2 {

    public Inner2() {
    }
  }

  public static class Inner3 {

    public Inner3() {
    }
  }

  public static class Inner4 {

    public Inner4() {
    }
  }
}
