// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InlineFunctionalInterfaceMethodImplementedByLambdasTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InlineFunctionalInterfaceMethodImplementedByLambdasTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineFunctionalInterfaceMethodImplementedByLambdasTest.class)
        .addKeepMainRule(TestClass.class)
        .enableClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    if (parameters.isDexRuntime()) {
      // TODO(b/145271928): Should be able to inline I.m().
      assertThat(inspector.clazz(I.class), isPresent());
    }

    // TODO(b/145271928): Should be able to inline A.m().
    assertThat(inspector.clazz(A.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      ((I) new A()).m();
      ((I) () -> System.out.println(" world!")).m();
    }
  }

  interface I {

    void m();
  }

  @NeverClassInline
  static class A implements I {

    @Override
    public void m() {
      System.out.print("Hello");
    }
  }
}
