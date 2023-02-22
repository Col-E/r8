// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringConcatenationTest extends TestBase {
  private static final Class<?> MAIN = StringConcatenationTestClass.class;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      // trivialSequence
      "xyz",
      // builderWithInitialValue
      "Hello,R8",
      // builderWithCapacity
      "42",
      // nonStringArgs
      "42",
      // typeConversion
      "0.14 0 false null",
      // typeConversion_withPhis
      "3.14 3 0",
      // nestedBuilders_appendBuilderItself
      "Hello,R8",
      // nestedBuilders_appendBuilderResult
      "Hello,R8",
      // nestedBuilders_conditional
      "Hello,R8",
      // concatenatedBuilders_init
      "Hello,R8",
      // concatenatedBuilders_append
      "Hello,R8",
      // concatenatedBuilders_conditional
      "Hello,R8",
      // simplePhi
      "Hello,",
      "Hello,D8",
      // phiAtInit
      "Hello,R8",
      // phiWithDifferentInits
      "Hello,R8",
      // conditionalPhiWithoutAppend
      "initial:suffix",
      // loop
      "na;na;na;na;na;na;na;na;Batman!",
      // loopWithBuilder
      "na;na;na;na;na;na;na;na;Batman!"
  );

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

  private void test(SingleTestRunResult<?> result, boolean isR8, boolean isReleaseMode)
      throws Exception {
    // TODO(b/154899065): The lack of subtyping made the escape analysis to regard
    //    StringBuilder#toString as an alias-introducing instruction.
    //    For now, debug v.s. release mode of D8 have the same result.
    //    Use library modeling to allow this optimization.

    // Smaller is better in general. If the counter part is zero, that means non-string arguments
    // are used, and in that case bigger is better.
    // If the fixed count is used, that means StringBuilderOptimization should keep things as-is
    // or there would be no observable differences (# of builders could be different).
    int expectedCount;

    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);

    MethodSubject method = mainClass.uniqueMethodWithOriginalName("unusedBuilder");
    if (isR8) {
      assertThat(method, not(isPresent()));
    } else {
      assertThat(method, isPresent());
      assertEquals(0, countConstString(method));
    }

    method = mainClass.uniqueMethodWithOriginalName("trivialSequence");
    assertThat(method, isPresent());
    expectedCount = isReleaseMode ? 1 : 3;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("builderWithInitialValue");
    assertThat(method, isPresent());
    expectedCount = isReleaseMode ? 1 : 3;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("builderWithCapacity");
    assertThat(method, isPresent());
    expectedCount = isReleaseMode ? 1 : 0;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("nonStringArgs");
    assertThat(method, isPresent());
    expectedCount = isReleaseMode ? 1 : 0;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("typeConversion");
    assertThat(method, isPresent());
    expectedCount = isReleaseMode ? 1 : 0;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("typeConversion_withPhis");
    assertThat(method, isPresent());
    assertEquals(0, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("nestedBuilders_appendBuilderItself");
    assertThat(method, isPresent());
    assertEquals(isReleaseMode ? 1 : 3, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("nestedBuilders_appendBuilderResult");
    assertThat(method, isPresent());
    assertEquals(isReleaseMode ? 1 : 3, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("nestedBuilders_conditional");
    assertThat(method, isPresent());
    assertEquals(isReleaseMode ? 3 : 4, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("concatenatedBuilders_init");
    assertThat(method, isPresent());
    assertEquals(isReleaseMode ? 1 : 2, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("concatenatedBuilders_append");
    assertThat(method, isPresent());
    assertEquals(isReleaseMode ? 1 : 2, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("concatenatedBuilders_conditional");
    assertThat(method, isPresent());
    assertEquals(isReleaseMode ? 2 : 4, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("simplePhi");
    assertThat(method, isPresent());
    assertEquals(isReleaseMode ? 3 : 4, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("phiAtInit");
    assertThat(method, isPresent());
    assertEquals(3, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("phiWithDifferentInits");
    assertThat(method, isPresent());
    assertEquals(3, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("conditionalPhiWithoutAppend");
    assertThat(method, isPresent());
    assertEquals(isReleaseMode ? 2 : 3, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("loop");
    assertThat(method, isPresent());
    assertEquals(3, countConstString(method));

    method = mainClass.uniqueMethodWithOriginalName("loopWithBuilder");
    assertThat(method, isPresent());
    assertEquals(2, countConstString(method));
  }

  private long countConstString(MethodSubject method) {
    return method.streamInstructions().filter(i -> i.isConstString(JumboStringMode.ALLOW)).count();
  }

  @Test
  public void testD8Debug() throws Exception {
    parameters.assumeDexRuntime();
    test(
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT),
        false,
        false);
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    test(
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT),
        false,
        true);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue("CF does not rewrite move results.", parameters.isDexRuntime());

    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .addDontObfuscate()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, true, true);
  }
}
