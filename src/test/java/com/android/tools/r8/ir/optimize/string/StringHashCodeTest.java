// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringHashCodeTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      String.valueOf("GONE".hashCode()),
      String.valueOf("Shared".hashCode()),
      String.valueOf("Shared".hashCode()),
      String.valueOf("Another_shared".hashCode()),
      "Another_shared",
      String.valueOf("ABC".hashCode())
  );
  private static final Class<?> MAIN = TestClass.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

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
      SingleTestRunResult<?> result, int expectedStringHashCodeCount, int expectedConstNumberCount)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedStringHashCodeCount, countCall(mainMethod, "String", "hashCode"));
    assertEquals(expectedConstNumberCount, countNonZeroConstNumber(mainMethod));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    D8TestRunResult result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 2, 3);

    result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 5, 0);
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
    test(result, 0, parameters.isDexRuntime() ? 4 : 5);
  }

  static class TestClass {
    static String simpleInlineable() {
      return "Shared";
    }

    @NeverInline
    static int npe() {
      String n = null;
      // Cannot be computed at compile time.
      return n.hashCode();
    }

    public static void main(String[] args) {
      String s1 = "GONE";
      // Can be computed at compile time: constCount++
      System.out.println(s1.hashCode());

      String s2 = simpleInlineable();
      // Depends on inlining: constCount++
      System.out.println(s2.hashCode());
      String s3 = simpleInlineable();
      System.out.println(s3.hashCode());

      String s4 = "Another_shared";
      // Can be computed at compile time: constCount++
      System.out.println(s4.hashCode());
      System.out.println(s4);

      // Make sure this is not optimized in DEBUG mode.
      int l = "ABC".hashCode();
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
