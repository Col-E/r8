// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstructorWithExceptionalControlFlowTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ConstructorWithExceptionalControlFlowTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ConstructorWithExceptionalControlFlowTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("false");
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new A().alwaysFalse);
    }
  }

  static class A {

    boolean alwaysFalse;

    A() {
      try {
        throwRuntimeException();
      } catch (Exception e) {
        return;
      }
      alwaysFalse = true;
    }

    @NeverInline
    static void throwRuntimeException() {
      if (System.currentTimeMillis() >= 0) {
        throw new RuntimeException();
      }
    }
  }
}
