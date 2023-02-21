// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumevalues;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeValuesForConstantValuedFieldTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AssumeValuesForConstantValuedFieldTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(AssumeValuesForConstantValuedFieldTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-assumevalues class * { static boolean field return false; }")
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "TestClass.field = false", "A.field = false", "B.<clinit>()", "B.field = false");
  }

  static class TestClass {

    static boolean field = true;

    public static void main(String[] args) {
      if (field) {
        System.out.println("TestClass.field = true");
      } else {
        System.out.println("TestClass.field = false");
      }
      if (A.field) {
        System.out.println("A.field = true");
      } else {
        System.out.println("A.field = false");
      }
      if (B.field) {
        System.out.println("B.field = true");
      } else {
        System.out.println("B.field = false");
      }
    }
  }

  static class A {

    static boolean field = true;
  }

  static class B {

    static {
      System.out.println("B.<clinit>()");
    }

    static boolean field = true;
  }
}
