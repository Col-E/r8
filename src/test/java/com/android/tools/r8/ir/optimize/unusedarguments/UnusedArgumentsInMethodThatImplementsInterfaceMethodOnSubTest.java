// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedArgumentsInMethodThatImplementsInterfaceMethodOnSubTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnusedArgumentsInMethodThatImplementsInterfaceMethodOnSubTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(UnusedArgumentsInMethodThatImplementsInterfaceMethodOnSubTest.class)
        .addKeepMainRule(TestClass.class)
        .enableMergeAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello from A", "Hello from C");
  }

  static class TestClass {

    public static void main(String[] args) {
      for (I o : new I[] {new B(), new C()}) {
        o.method(new Object());
      }
    }
  }

  interface I {

    void method(Object o);
  }

  @NeverMerge
  static class A {

    public void method(Object unused) {
      System.out.println("Hello from A");
    }
  }

  static class B extends A implements I {}

  static class C implements I {

    @Override
    public void method(Object o) {
      if (o != null) {
        System.out.println("Hello from C");
      }
    }
  }
}
