// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssociativeIntTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Associative",
          "7",
          "47",
          "-2147483644",
          "-2147483643",
          "12",
          "252",
          "-6",
          "0",
          "2",
          "2",
          "2",
          "0",
          "3",
          "43",
          "2147483647",
          "-2147483645",
          "3",
          "43",
          "2147483646",
          "-2147483647",
          "Shift",
          "64",
          "1344",
          "-32",
          "0",
          "0",
          "1",
          "67108863",
          "-67108864",
          "0",
          "1",
          "67108863",
          "67108864",
          "Sub",
          "-1",
          "-41",
          "-2147483646",
          "-2147483647",
          "-3",
          "37",
          "2147483642",
          "2147483643",
          "Mixed",
          "3",
          "43",
          "-2147483648",
          "-2147483647",
          "3",
          "-37",
          "-2147483642",
          "-2147483643",
          "-1",
          "-41",
          "-2147483646",
          "-2147483647",
          "25",
          "-15",
          "-2147483620",
          "-2147483621",
          "3",
          "43",
          "-2147483648",
          "-2147483647",
          "3",
          "43",
          "-2147483648",
          "-2147483647",
          "1",
          "41",
          "2147483646",
          "2147483647",
          "Double Associative",
          "12",
          "52",
          "84",
          "1764",
          "2",
          "2",
          "7",
          "47",
          "7",
          "7",
          "4",
          "44",
          "Double Shift",
          "128",
          "2688",
          "-2147483648",
          "-2147483648",
          "0",
          "0",
          "0",
          "0",
          "Double Sub",
          "-1",
          "-41",
          "-10",
          "30",
          "Double Mixed",
          "-4",
          "36",
          "7",
          "-33",
          "-4",
          "36",
          "5",
          "45");

  @Parameter(0)
  public boolean enableConstantArgumentAnnotations;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, @KeepConstantArguments: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testD8() throws Exception {
    assumeFalse(enableConstantArgumentAnnotations);
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
        .applyIf(
            enableConstantArgumentAnnotations,
            R8FullTestBuilder::enableConstantArgumentAnnotations,
            R8FullTestBuilder::addConstantArgumentAnnotations)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Main.class);
    for (FoundMethodSubject method :
        clazz.allMethods(m -> m.getParameters().size() > 0 && m.getParameter(0).is("int"))) {
      int numberOfExpectedIntBinops = method.getOriginalName().contains("NotSimplified") ? 2 : 1;
      if (!enableConstantArgumentAnnotations) {
        switch (method.getOriginalName()) {
          case "andDouble":
          case "orDoubleToConst":
          case "shlDoubleToConst":
          case "shrDouble":
          case "ushrDouble":
            numberOfExpectedIntBinops = 0;
            break;
          default:
            break;
        }
      }
      assertEquals(
          method.getOriginalName(),
          numberOfExpectedIntBinops,
          method
              .streamInstructions()
              .filter(i -> i.isIntArithmeticBinop() || i.isIntLogicalBinop())
              .count());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      simple();
      doubleOps();
    }

    @NeverInline
    private static void simple() {
      // Associative + * & | ^.
      System.out.println("Associative");
      add(2);
      add(42);
      add(Integer.MAX_VALUE);
      add(Integer.MIN_VALUE);
      mul(2);
      mul(42);
      mul(Integer.MAX_VALUE);
      mul(Integer.MIN_VALUE);
      and(2);
      and(42);
      and(Integer.MAX_VALUE);
      and(Integer.MIN_VALUE);
      or(2);
      or(42);
      or(Integer.MAX_VALUE);
      or(Integer.MIN_VALUE);
      xor(2);
      xor(42);
      xor(Integer.MAX_VALUE);
      xor(Integer.MIN_VALUE);

      // Shift composition.
      System.out.println("Shift");
      shl(2);
      shl(42);
      shl(Integer.MAX_VALUE);
      shl(Integer.MIN_VALUE);
      shr(2);
      shr(42);
      shr(Integer.MAX_VALUE);
      shr(Integer.MIN_VALUE);
      ushr(2);
      ushr(42);
      ushr(Integer.MAX_VALUE);
      ushr(Integer.MIN_VALUE);

      // Special for -.
      System.out.println("Sub");
      sub(2);
      sub(42);
      sub(Integer.MAX_VALUE);
      sub(Integer.MIN_VALUE);
      sub2(2);
      sub2(42);
      sub2(Integer.MAX_VALUE);
      sub2(Integer.MIN_VALUE);

      // Mixed for + and -.
      System.out.println("Mixed");
      addSub(2);
      addSub(42);
      addSub(Integer.MAX_VALUE);
      addSub(Integer.MIN_VALUE);
      subAdd(2);
      subAdd(42);
      subAdd(Integer.MAX_VALUE);
      subAdd(Integer.MIN_VALUE);
      addSubNotSimplified_1(2);
      addSubNotSimplified_1(42);
      addSubNotSimplified_1(Integer.MAX_VALUE);
      addSubNotSimplified_1(Integer.MIN_VALUE);
      addSubNotSimplified_2(2);
      addSubNotSimplified_2(42);
      addSubNotSimplified_2(Integer.MAX_VALUE);
      addSubNotSimplified_2(Integer.MIN_VALUE);
      addSubNotSimplified_3(2);
      addSubNotSimplified_3(42);
      addSubNotSimplified_3(Integer.MAX_VALUE);
      addSubNotSimplified_3(Integer.MIN_VALUE);
      addSub2(2);
      addSub2(42);
      addSub2(Integer.MAX_VALUE);
      addSub2(Integer.MIN_VALUE);
      subAdd2(2);
      subAdd2(42);
      subAdd2(Integer.MAX_VALUE);
      subAdd2(Integer.MIN_VALUE);
    }

    @NeverInline
    private static void doubleOps() {
      // Associative + * & | ^.
      System.out.println("Double Associative");
      addDouble(2);
      addDouble(42);
      mulDouble(2);
      mulDouble(42);
      andDouble(2);
      andDouble(42);
      orDouble(2);
      orDouble(42);
      orDoubleToConst(0);
      orDoubleToConst(7);
      xorDouble(2);
      xorDouble(42);

      // Shift composition.
      System.out.println("Double Shift");
      shlDouble(2);
      shlDouble(42);
      shlDoubleToConst(1);
      shlDoubleToConst(-1);
      shrDouble(2);
      shrDouble(42);
      ushrDouble(2);
      ushrDouble(42);

      // Special for -.
      System.out.println("Double Sub");
      subDouble(2);
      subDouble(42);
      sub2Double(2);
      sub2Double(42);

      // Mixed for + and -.
      System.out.println("Double Mixed");
      addSubDouble(2);
      addSubDouble(42);
      subAddDouble(2);
      subAddDouble(42);
      addSub2Double(2);
      addSub2Double(42);
      subAdd2Double(2);
      subAdd2Double(42);
    }

    @NeverInline
    public static void add(int x) {
      System.out.println(3 + x + 2);
    }

    @NeverInline
    public static void mul(int x) {
      System.out.println(3 * x * 2);
    }

    @NeverInline
    public static void and(int x) {
      System.out.println(3 & x & 2);
    }

    @NeverInline
    public static void or(int x) {
      System.out.println(3 | x | 2);
    }

    @NeverInline
    public static void xor(int x) {
      System.out.println(3 ^ x ^ 2);
    }

    @NeverInline
    public static void shl(int x) {
      System.out.println(x << 2 << 3);
    }

    @NeverInline
    public static void shr(int x) {
      System.out.println(x >> 2 >> 3);
    }

    @NeverInline
    public static void ushr(int x) {
      System.out.println(x >>> 2 >>> 3);
    }

    @NeverInline
    public static void sub(int x) {
      System.out.println(3 - x - 2);
    }

    @NeverInline
    public static void sub2(int x) {
      System.out.println(x - 3 - 2);
    }

    @NeverInline
    public static void addSub(int x) {
      System.out.println(3 + x - 2);
    }

    @NeverInline
    public static void addSub2(int x) {
      System.out.println(x + 3 - 2);
    }

    @NeverInline
    public static void addSubNotSimplified_1(int x) {
      System.out.println(14 - (x + 13));
    }

    @NeverInline
    public static void addSubNotSimplified_2(int x) {
      System.out.println(14 - (x - 13));
    }

    @NeverInline
    public static void addSubNotSimplified_3(int x) {
      System.out.println(14 - (13 - x));
    }

    @NeverInline
    public static void subAdd(int x) {
      System.out.println(3 - x + 2);
    }

    @NeverInline
    public static void subAdd2(int x) {
      System.out.println(x - 3 + 2);
    }

    @NeverInline
    public static void addDouble(int x) {
      System.out.println(3 + x + 2 + 5);
    }

    @NeverInline
    public static void mulDouble(int x) {
      System.out.println(3 * x * 2 * 7);
    }

    @KeepConstantArguments
    @NeverInline
    public static void andDouble(int x) {
      System.out.println(3 & x & 2 & 7);
    }

    @NeverInline
    public static void orDouble(int x) {
      System.out.println(3 | x | 2 | 7);
    }

    @KeepConstantArguments
    @NeverInline
    public static void orDoubleToConst(int x) {
      System.out.println(1 | x | 2 | 4);
    }

    @NeverInline
    public static void xorDouble(int x) {
      System.out.println(3 ^ x ^ 2 ^ 7);
    }

    @NeverInline
    public static void shlDouble(int x) {
      System.out.println(x << 2 << 3 << 1);
    }

    @KeepConstantArguments
    @NeverInline
    public static void shlDoubleToConst(int x) {
      System.out.println(x << 8 << 8 << 15);
    }

    @KeepConstantArguments
    @NeverInline
    public static void shrDouble(int x) {
      System.out.println(x >> 2 >> 3 >> 1);
    }

    @KeepConstantArguments
    @NeverInline
    public static void ushrDouble(int x) {
      System.out.println(x >>> 2 >>> 3 >>> 1);
    }

    @NeverInline
    public static void subDouble(int x) {
      System.out.println(3 - x - 2);
    }

    @NeverInline
    public static void sub2Double(int x) {
      System.out.println(x - 3 - 2 - 7);
    }

    @NeverInline
    public static void addSubDouble(int x) {
      System.out.println(3 + x - 2 - 7);
    }

    @NeverInline
    public static void addSub2Double(int x) {
      System.out.println(x + 3 - 2 - 7);
    }

    @NeverInline
    public static void subAddDouble(int x) {
      System.out.println(3 - x + 2 + 4);
    }

    @NeverInline
    public static void subAdd2Double(int x) {
      System.out.println(x - 3 + 2 + 4);
    }
  }
}
