// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.checkcast;


import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceArrayCheckCastTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "class [L" + Impl1.class.getTypeName() + ";",
          "class [L" + Impl1.class.getTypeName() + ";");

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRules("-keep class ** { *; }")
        .setMinApi(parameters)
        .allowStdoutMessages()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  // See b/223424356 (and basically the code back from b/69826014#comment3).
  interface I {}

  static class Impl1 implements I {}

  static class Impl2 implements I {}

  static class TestClass {

    public static Impl1[] getArrayOfImpl1() {
      return new Impl1[] {new Impl1()};
    }

    public static Impl2[] getArrayOfImpl2() {
      return new Impl2[] {new Impl2()};
    }

    public static I[] getArrayOfI(boolean b) {
      if (b) {
        return TestClass.getArrayOfImpl1();
      } else {
        return TestClass.getArrayOfImpl2();
      }
    }

    public static I[] getArrayOfIWithCast(boolean b) {
      Object[] arrayOfI;
      if (b) {
        arrayOfI = TestClass.getArrayOfImpl1();
      } else {
        arrayOfI = TestClass.getArrayOfImpl2();
      }
      return (I[]) arrayOfI;
    }

    public static void main(String[] args) {
      System.out.println(getArrayOfIWithCast(args.length == 0).getClass());
      System.out.println(getArrayOfI(args.length == 0).getClass());
    }
  }
}
