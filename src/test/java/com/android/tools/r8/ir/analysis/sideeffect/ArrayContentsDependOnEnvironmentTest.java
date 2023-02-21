// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import com.android.tools.r8.AssumeNoSideEffects;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayContentsDependOnEnvironmentTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ArrayContentsDependOnEnvironmentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ArrayContentsDependOnEnvironmentTest.class)
        .addKeepMainRule(TestClass.class)
        .enableAssumeNoSideEffectsAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("42");
  }

  static class TestClass {

    static int f = 42;

    public static void main(String[] args) {
      // Trigger A.<clinit>(), this will set A.values to [42].
      A.empty();
      // Unset f, such that the below will read -1 if we failed to trigger class initialization.
      f = -1;
      // Print 42.
      System.out.println(A.values[0]);
    }
  }

  static class A {

    static long[] values = new long[] {getFourtyTwo()};

    @AssumeNoSideEffects
    @NeverInline
    static long getFourtyTwo() {
      return TestClass.f;
    }

    static void empty() {
      // Intentionally empty.
    }
  }
}
