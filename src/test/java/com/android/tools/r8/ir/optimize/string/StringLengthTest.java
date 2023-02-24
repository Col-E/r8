// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringLengthTest extends TestBase {
  private static final String JAVA_OUTPUT =
      StringUtils.lines("4", "6", "Shared", "14", "Another_shared", "2", "1", "🂡", "3");
  private static final Class<?> MAIN = TestClass.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public StringLengthTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private long countNonZeroConstNumber(MethodSubject method) {
    return method.streamInstructions().filter(InstructionSubject::isConstNumber).count()
        - method.streamInstructions().filter(instr -> instr.isConstNumber(0)).count();
  }

  private void test(
      SingleTestRunResult<?> result, int expectedStringLengthCount, int expectedConstNumberCount)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedStringLengthCount, countCall(mainMethod, "String", "length"));
    assertEquals(expectedConstNumberCount, countNonZeroConstNumber(mainMethod));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());
    D8TestRunResult result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN);
    result.assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1, 5);

    result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN);
    result.assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 6, 1);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0, parameters.isDexRuntime() ? 6 : 7);
  }

  public static class TestClass {

    static String simpleInlineable() {
      return "Shared";
    }

    @NeverInline
    static int npe() {
      String n = null;
      // Cannot be computed at compile time.
      return n.length();
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
      String s1 = "GONE";
      // Can be computed at compile time: constCount++
      System.out.println(s1.length());

      String s2 = simpleInlineable();
      // Depends on inlining: constCount++
      System.out.println(s2.length());
      String s3 = simpleInlineable();
      System.out.println(s3);

      String s4 = "Another_shared";
      // Can be computed at compile time: constCount++
      System.out.println(s4.length());
      System.out.println(s4);

      String s5 = "\uD83C\uDCA1"; // U+1F0A1
      // Can be computed at compile time: constCount++
      System.out.println(s5.length());
      // Even reusable: should not increase any counts.
      System.out.println(s5.codePointCount(0, s5.length()));
      // The true below will add to the constant count.
      PrintStream ps = new PrintStream(System.out, true, "UTF-8");
      ps.println(s5);

      // Make sure this is not optimized in DEBUG mode.
      int l = "ABC".length();
      System.out.println(l);

      try {
        npe();
        throw new AssertionError("Expect to raise NPE");
      } catch (NullPointerException npe) {
        // expected
      }
    }
  }
}
