// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public StringConcatenationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void test(TestRunResult result, boolean isR8, boolean isReleaseMode) throws Exception {
    // TODO(b/114002137): The lack of subtyping made the escape analysis to regard
    //    StringBuilder#toString as an alias-introducing instruction.
    //    For now, debug v.s. release mode of D8 have the same result.

    // Smaller is better in general. If the counter part is zero, that means non-string arguments
    // are used, and in that case bigger is better.
    // If the fixed count is used, that means StringBuilderOptimization should keep things as-is
    // or there would be no observable differences (# of builders could be different).
    int expectedCount;

    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);

    MethodSubject method = mainClass.uniqueMethodWithName("unusedBuilder");
    if (isR8) {
      assertThat(method, not(isPresent()));
    } else {
      assertThat(method, isPresent());
      assertEquals(0, countConstString(method));
    }

    method = mainClass.uniqueMethodWithName("trivialSequence");
    assertThat(method, isPresent());
    expectedCount = isR8 ? 1 : 3;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithName("builderWithInitialValue");
    assertThat(method, isPresent());
    expectedCount = isR8 ? 1 : 3;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithName("builderWithCapacity");
    assertThat(method, isPresent());
    expectedCount = isR8 ? 1 : 0;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithName("nonStringArgs");
    assertThat(method, isPresent());
    expectedCount = isR8 ? 1 : 0;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithName("typeConversion");
    assertThat(method, isPresent());
    expectedCount = isR8 ? 1 : 0;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithName("typeConversion_withPhis");
    assertThat(method, isPresent());
    assertEquals(0, countConstString(method));

    method = mainClass.uniqueMethodWithName("nestedBuilders_appendBuilderItself");
    assertThat(method, isPresent());
    // TODO(b/113859361): merge builders
    expectedCount = 3;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithName("nestedBuilders_appendBuilderResult");
    assertThat(method, isPresent());
    // TODO(b/113859361): merge builders
    expectedCount = 3;
    assertEquals(expectedCount, countConstString(method));

    method = mainClass.uniqueMethodWithName("simplePhi");
    assertThat(method, isPresent());
    assertEquals(4, countConstString(method));

    method = mainClass.uniqueMethodWithName("phiAtInit");
    assertThat(method, isPresent());
    assertEquals(3, countConstString(method));

    method = mainClass.uniqueMethodWithName("phiWithDifferentInits");
    assertThat(method, isPresent());
    assertEquals(3, countConstString(method));

    method = mainClass.uniqueMethodWithName("conditionalPhiWithoutAppend");
    assertThat(method, isPresent());
    assertEquals(3, countConstString(method));

    method = mainClass.uniqueMethodWithName("loop");
    assertThat(method, isPresent());
    assertEquals(3, countConstString(method));

    method = mainClass.uniqueMethodWithName("loopWithBuilder");
    assertThat(method, isPresent());
    assertEquals(2, countConstString(method));
  }

  private long countConstString(MethodSubject method) {
    return method.streamInstructions().filter(i -> i.isConstString(JumboStringMode.ALLOW)).count();
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, false, false);

    result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, false, true);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue("CF does not rewrite move results.", parameters.isDexRuntime());

    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .noMinification()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, true, true);
  }
}
