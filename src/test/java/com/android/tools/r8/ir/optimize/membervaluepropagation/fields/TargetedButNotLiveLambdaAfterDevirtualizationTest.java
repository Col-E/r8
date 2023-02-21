// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.fields;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Reproduction of b/209475782. */
@RunWith(Parameterized.class)
public class TargetedButNotLiveLambdaAfterDevirtualizationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    static I f;
    static I f2;

    public static void main(String[] args) {
      if (alwaysFalse()) {
        setFields();
      }
      if (unknownButFalse()) {
        callLambdaMethods();
      }
    }

    static boolean alwaysFalse() {
      return false;
    }

    static boolean unknownButFalse() {
      return System.currentTimeMillis() < 0;
    }

    @NeverInline
    static void setFields() {
      f = () -> System.out.println("Foo!");
      f2 = () -> System.out.println("Bar!");
    }

    @NeverInline
    static void callLambdaMethods() {
      f.m();
      f2.m();
    }
  }

  interface I {

    void m();
  }
}
