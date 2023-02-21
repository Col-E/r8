// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b152800551;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// This is a reproduction of b/152800551.
@RunWith(Parameterized.class)
public class StaticizerGetterRewritingRegressionTest extends TestBase {

  private static final String EXPECTED =
      StringUtils.lines(
          "S::foo a 1", "S::foo a 2", "S::foo a 3", "S::foo b 1", "S::foo b 2", "S::foo b 3");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StaticizerGetterRewritingRegressionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  static class S {

    private static S f = new S();

    public static S get() {
      return f;
    }

    public void foo() {
      // Double call of foo so single call inlining does not trigger.
      foo("a");
      foo("b");
    }

    @NeverInline
    public void foo(String s) {
      System.out.println("S::foo " + s + " 1");
      System.out.println("S::foo " + s + " 2");
      System.out.println("S::foo " + s + " 3");
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      S.get().foo();
    }
  }
}
