// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DeadConstructorWithCycleTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DeadConstructorWithCycleTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(DeadConstructorWithCycleTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(inspector -> assertThat(inspector.clazz(A.class), not(isPresent())))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  static class TestClass {

    public static void main(String[] args) {
      Object o1 = null;
      Object o2 = null;
      for (int i = 1; i <= 2; i++) {
        o1 = new A(o1, o2);
        o2 = new A(o1, o2);
      }
    }
  }

  @NeverClassInline
  static class A {

    Object f1;
    Object f2;

    A(Object o1, Object o2) {
      this.f1 = o1;
      this.f2 = o2;
    }
  }
}
