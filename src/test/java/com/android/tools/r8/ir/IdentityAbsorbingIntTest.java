// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class IdentityAbsorbingIntTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483647",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "0",
          "2147483647",
          "2147483647",
          "2147483647",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "2147483646",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "0",
          "2147483646",
          "2147483646",
          "2147483646",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "0",
          "-2147483648",
          "-2147483648",
          "-2147483648",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "0",
          "-2147483647",
          "-2147483647",
          "-2147483647",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "1",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "0",
          "1",
          "1",
          "1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "-1",
          "0",
          "0",
          "0",
          "0",
          "-1",
          "-1",
          "-1");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().withDexRuntimes().withAllApiLevels().build();
  }

  public IdentityAbsorbingIntTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void inspect(CodeInspector inspector) {
    inspector
        .clazz(Main.class)
        .forAllMethods(
            m ->
                assertTrue(
                    m.streamInstructions()
                        .noneMatch(i -> i.isIntLogicalBinop() || i.isIntArithmeticBinop())));
  }

  static class Main {

    public static void main(String[] args) {
      intTests(Integer.MAX_VALUE);
      intTests(Integer.MAX_VALUE - 1);
      intTests(Integer.MIN_VALUE);
      intTests(Integer.MIN_VALUE + 1);
      intTests(System.currentTimeMillis() > 0 ? 0 : 1);
      intTests(System.currentTimeMillis() > 0 ? 1 : 9);
      intTests(System.currentTimeMillis() > 0 ? -1 : 1);
    }

    private static void intTests(int val) {
      identityIntTest(val);
      absorbingIntTest(val);
      identityDoubleIntTest(val);
      absorbingDoubleIntTest(val);
      chainIntTest(val);
      associativeIdentityIntTest(val);
    }

    @NeverInline
    private static void identityDoubleIntTest(int val) {
      System.out.println(val + 0 + 0);
      System.out.println(0 + val + 0);
      System.out.println(0 + 0 + val);
      System.out.println(val - 0 - 0);
      System.out.println(val * 1 * 1);
      System.out.println(1 * val * 1);
      System.out.println(1 * 1 * val);
      System.out.println(val / 1 / 1);

      System.out.println(val & -1 & -1);
      System.out.println(-1 & val & -1);
      System.out.println(-1 & -1 & val);
      System.out.println(val | 0 | 0);
      System.out.println(0 | val | 0);
      System.out.println(0 | 0 | val);
      System.out.println(val ^ 0 ^ 0);
      System.out.println(0 ^ val ^ 0);
      System.out.println(0 ^ 0 ^ val);
      System.out.println(val << 0 << 0);
      System.out.println(val >> 0 >> 0);
      System.out.println(val >>> 0 >>> 0);
    }

    @NeverInline
    private static void identityIntTest(int val) {
      System.out.println(val + 0);
      System.out.println(0 + val);
      System.out.println(val - 0);
      System.out.println(val * 1);
      System.out.println(1 * val);
      System.out.println(val / 1);

      System.out.println(val & -1);
      System.out.println(-1 & val);
      System.out.println(val | 0);
      System.out.println(0 | val);
      System.out.println(val ^ 0);
      System.out.println(0 ^ val);
      System.out.println(val << 0);
      System.out.println(val >> 0);
      System.out.println(val >>> 0);
    }

    @NeverInline
    private static void associativeIdentityIntTest(int val) {
      int minusOne = -1;
      System.out.println(val + 1 + minusOne);
      System.out.println(val + 1 - 1);
      System.out.println(val - 1 - minusOne);
    }

    @NeverInline
    private static void absorbingDoubleIntTest(int val) {
      System.out.println(val * 0 * 0);
      System.out.println(0 * val * 0);
      System.out.println(0 * 0 * val);
      // val would need to be proven non zero.
      // System.out.println(0 / val);
      // System.out.println(0 % val);

      System.out.println(0 & 0 & val);
      System.out.println(0 & val & 0);
      System.out.println(val & 0 & 0);
      System.out.println(-1 | -1 | val);
      System.out.println(-1 | val | -1);
      System.out.println(val | -1 | -1);
      System.out.println(0 << 0 << val);
      System.out.println(0 >> 0 >> val);
      System.out.println(0 >>> 0 >>> val);
    }

    @NeverInline
    private static void absorbingIntTest(int val) {
      System.out.println(val * 0);
      System.out.println(0 * val);
      // val would need to be proven non zero.
      // System.out.println(0 / val);
      // System.out.println(0 % val);

      System.out.println(0 & val);
      System.out.println(val & 0);
      System.out.println(-1 | val);
      System.out.println(val | -1);
      System.out.println(0 << val);
      System.out.println(0 >> val);
      System.out.println(0 >>> val);
    }

    private static void chainIntTest(int val) {
      int abs = System.currentTimeMillis() > 0 ? val * 0 : 0 * val;
      System.out.println(abs * val);
    }
  }
}
