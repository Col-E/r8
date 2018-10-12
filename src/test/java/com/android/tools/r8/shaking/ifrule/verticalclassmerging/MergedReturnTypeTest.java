// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

public class MergedReturnTypeTest extends MergedTypeBaseTest {

  static class TestClass {

    public static void main(String[] args) {
      System.out.print(method().getClass().getName());
    }

    public static A method() {
      return new B();
    }
  }

  public MergedReturnTypeTest(Backend backend, boolean enableVerticalClassMerging) {
    super(backend, enableVerticalClassMerging);
  }

  @Override
  public Class<?> getTestClass() {
    return TestClass.class;
  }

  @Override
  public String getConditionForProguardIfRule() {
    return "-if class **$TestClass { **$A method(); }";
  }

  @Override
  public String getExpectedStdout() {
    return B.class.getName();
  }
}
