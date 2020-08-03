// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Reproduction of b/160901582 where we inline class with escaping instance variable. */
@RunWith(Parameterized.class)
public class ClassInlinerInstanceEscapeViaPhiTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerInstanceEscapeViaPhiTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(ClassInlinerInstanceEscapeViaPhiTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("false");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlinerInstanceEscapeViaPhiTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("false");
  }

  static class A {

    public A() {
      B.foo(System.nanoTime() > 0 ? this : null);
    }
  }

  static class B {

    static void foo(A a) {
      System.out.println((System.nanoTime() > 0 ? a : null) == null);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new A();
    }
  }
}
