// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssociativeLongTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "Associative",
          "7",
          "47",
          "-9223372036854775804",
          "-9223372036854775803",
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
          "9223372036854775807",
          "-9223372036854775805",
          "3",
          "43",
          "9223372036854775806",
          "-9223372036854775807",
          "Shift",
          "64",
          "1344",
          "-32",
          "0",
          "0",
          "1",
          "288230376151711743",
          "-288230376151711744",
          "0",
          "1",
          "288230376151711743",
          "288230376151711744",
          "Sub",
          "-1",
          "-41",
          "-9223372036854775806",
          "-9223372036854775807",
          "-3",
          "37",
          "9223372036854775802",
          "9223372036854775803",
          "Mixed",
          "3",
          "43",
          "-9223372036854775808",
          "-9223372036854775807",
          "3",
          "-37",
          "-9223372036854775802",
          "-9223372036854775803",
          "3",
          "43",
          "-9223372036854775808",
          "-9223372036854775807",
          "1",
          "41",
          "9223372036854775806",
          "9223372036854775807",
          "Double Associative",
          "12",
          "52",
          "84",
          "1764",
          "2",
          "2",
          "7",
          "47",
          "4",
          "44",
          "Double Shift",
          "128",
          "2688",
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
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().withDexRuntimes().withAllApiLevels().build();
  }

  public AssociativeLongTest(TestParameters parameters) {
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
    ClassSubject clazz = inspector.clazz(Main.class);
    for (FoundMethodSubject method :
        clazz.allMethods(m -> m.getParameters().size() > 0 && m.getParameter(0).is("long"))) {
      assertEquals(
          1,
          method
              .streamInstructions()
              .filter(i -> i.isIntArithmeticBinop() || i.isLongLogicalBinop())
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
      add(Long.MAX_VALUE);
      add(Long.MIN_VALUE);
      mul(2);
      mul(42);
      mul(Long.MAX_VALUE);
      mul(Long.MIN_VALUE);
      and(2);
      and(42);
      and(Long.MAX_VALUE);
      and(Long.MIN_VALUE);
      or(2);
      or(42);
      or(Long.MAX_VALUE);
      or(Long.MIN_VALUE);
      xor(2);
      xor(42);
      xor(Long.MAX_VALUE);
      xor(Long.MIN_VALUE);

      // Shift composition.
      System.out.println("Shift");
      shl(2);
      shl(42);
      shl(Long.MAX_VALUE);
      shl(Long.MIN_VALUE);
      shr(2);
      shr(42);
      shr(Long.MAX_VALUE);
      shr(Long.MIN_VALUE);
      ushr(2);
      ushr(42);
      ushr(Long.MAX_VALUE);
      ushr(Long.MIN_VALUE);

      // Special for -.
      System.out.println("Sub");
      sub(2);
      sub(42);
      sub(Long.MAX_VALUE);
      sub(Long.MIN_VALUE);
      sub2(2);
      sub2(42);
      sub2(Long.MAX_VALUE);
      sub2(Long.MIN_VALUE);

      // Mixed for + and -.
      System.out.println("Mixed");
      addSub(2);
      addSub(42);
      addSub(Long.MAX_VALUE);
      addSub(Long.MIN_VALUE);
      subAdd(2);
      subAdd(42);
      subAdd(Long.MAX_VALUE);
      subAdd(Long.MIN_VALUE);
      addSub2(2);
      addSub2(42);
      addSub2(Long.MAX_VALUE);
      addSub2(Long.MIN_VALUE);
      subAdd2(2);
      subAdd2(42);
      subAdd2(Long.MAX_VALUE);
      subAdd2(Long.MIN_VALUE);
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
      xorDouble(2);
      xorDouble(42);

      // Shift composition.
      System.out.println("Double Shift");
      shlDouble(2);
      shlDouble(42);
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
    public static void add(long x) {
      System.out.println(3L + x + 2L);
    }

    @NeverInline
    public static void mul(long x) {
      System.out.println(3L * x * 2L);
    }

    @NeverInline
    public static void and(long x) {
      System.out.println(3L & x & 2L);
    }

    @NeverInline
    public static void or(long x) {
      System.out.println(3L | x | 2L);
    }

    @NeverInline
    public static void xor(long x) {
      System.out.println(3L ^ x ^ 2L);
    }

    @NeverInline
    public static void shl(long x) {
      System.out.println(x << 2L << 3L);
    }

    @NeverInline
    public static void shr(long x) {
      System.out.println(x >> 2L >> 3L);
    }

    @NeverInline
    public static void ushr(long x) {
      System.out.println(x >>> 2L >>> 3L);
    }

    @NeverInline
    public static void sub(long x) {
      System.out.println(3L - x - 2L);
    }

    @NeverInline
    public static void sub2(long x) {
      System.out.println(x - 3L - 2L);
    }

    @NeverInline
    public static void addSub(long x) {
      System.out.println(3L + x - 2L);
    }

    @NeverInline
    public static void addSub2(long x) {
      System.out.println(x + 3L - 2L);
    }

    @NeverInline
    public static void subAdd(long x) {
      System.out.println(3L - x + 2L);
    }

    @NeverInline
    public static void subAdd2(long x) {
      System.out.println(x - 3L + 2L);
    }

    @NeverInline
    public static void addDouble(long x) {
      System.out.println(3L + x + 2L + 5);
    }

    @NeverInline
    public static void mulDouble(long x) {
      System.out.println(3L * x * 2L * 7L);
    }

    @NeverInline
    public static void andDouble(long x) {
      System.out.println(3L & x & 2L & 7L);
    }

    @NeverInline
    public static void orDouble(long x) {
      System.out.println(3L | x | 2L | 7L);
    }

    @NeverInline
    public static void xorDouble(long x) {
      System.out.println(3L ^ x ^ 2L ^ 7L);
    }

    @NeverInline
    public static void shlDouble(long x) {
      System.out.println(x << 2L << 3L << 1L);
    }

    @NeverInline
    public static void shrDouble(long x) {
      System.out.println(x >> 2L >> 3L >> 1L);
    }

    @NeverInline
    public static void ushrDouble(long x) {
      System.out.println(x >>> 2L >>> 3L >>> 1L);
    }

    @NeverInline
    public static void subDouble(long x) {
      System.out.println(3L - x - 2L);
    }

    @NeverInline
    public static void sub2Double(long x) {
      System.out.println(x - 3L - 2L - 7L);
    }

    @NeverInline
    public static void addSubDouble(long x) {
      System.out.println(3L + x - 2L - 7L);
    }

    @NeverInline
    public static void addSub2Double(long x) {
      System.out.println(x + 3L - 2L - 7L);
    }

    @NeverInline
    public static void subAddDouble(long x) {
      System.out.println(3L - x + 2L + 4L);
    }

    @NeverInline
    public static void subAdd2Double(long x) {
      System.out.println(x - 3L + 2L + 4L);
    }
  }
}
