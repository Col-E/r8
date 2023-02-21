// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassInitInlineForStaticGetterInSuperTypeTest extends TestBase {

  private static final String EXPECTED = "Hello World";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep class " + typeName(B.class) + " { <fields>; }")
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class A {

    public static boolean TEST = System.currentTimeMillis() == 0;

    @NeverInline
    public static boolean getTestStatic() {
      return TEST;
    }
  }

  public static class B extends A {

    static {
      B.TEST = true;
    }

    @NeverInline
    public static void triggerClassAInit(boolean test) {
      if (test) {
        System.out.println("Hello World");
      } else {
        System.out.println("Goodbye World");
      }
    }

    public static void inlinable() {
      triggerClassAInit(B.getTestStatic());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B.inlinable();
    }
  }
}
