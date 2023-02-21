// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;

import com.android.tools.r8.NeverClassInline;
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
public class ClassInitInlineForGetterTest extends TestBase {

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
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @NeverClassInline
  public static class B {

    public static boolean TEST = System.currentTimeMillis() == 0;

    @NeverInline
    public boolean getTest() {
      return TEST;
    }
  }

  public static class A {

    static {
      B.TEST = true;
    }

    @NeverInline
    public static void triggerClassAInit(boolean b) {
      if (b) {
        System.out.println("Hello World");
      } else {
        System.out.println("Goodbye World");
      }
    }

    public static void inlinable(B b) {
      if (b == null) {
        triggerClassAInit(false);
        return;
      }
      triggerClassAInit(b.getTest());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.inlinable(new B());
    }
  }
}
