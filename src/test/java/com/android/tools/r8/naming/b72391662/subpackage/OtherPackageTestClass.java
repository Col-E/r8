// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b72391662.subpackage;

import com.android.tools.r8.naming.b72391662.Interface;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class OtherPackageTestClass extends OtherPackageSuper implements Interface{
  public final int value;

  public OtherPackageTestClass() {
    this.value = 3;
  }

  public int getValue() {
    return value;
  }

  public static String staticMethod() {
    return "1";
  }

  public String instanceMethod() {
    return "2";
  }

  private int y(IntSupplier x) {
    return x.getAsInt();
  }

  public int x() {
    return y(super::returnFive);
  }

  public Object supplyNull() {
    System.out.print("B");
    return null;
  }

  public Object useSupplier(Supplier<Object> a) {
    return a.get();
  }
}
