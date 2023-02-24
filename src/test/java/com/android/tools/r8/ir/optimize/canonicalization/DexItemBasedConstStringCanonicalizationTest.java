// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.canonicalization;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ir.optimize.reflection.GetNameTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class CanonicalizationTestClass {}

class CanonicalizationTestMain {
  public static void main(String... args) {
    System.out.println(CanonicalizationTestClass.class.getSimpleName());
    // Canonicalized.
    System.out.println(CanonicalizationTestClass.class.getSimpleName());
    try {
      // Canonicalized (not a monitor block)
      System.out.println(CanonicalizationTestClass.class.getSimpleName());
    } catch (Exception e) {
      // Intentionally empty.
    }
  }
}

@RunWith(Parameterized.class)
public class DexItemBasedConstStringCanonicalizationTest extends GetNameTestBase {
  private static final Class<?> MAIN = CanonicalizationTestMain.class;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "CanonicalizationTestClass",
      "CanonicalizationTestClass",
      "CanonicalizationTestClass"
  );
  private static final String RENAMED_OUTPUT = StringUtils.lines(
      "a",
      "a",
      "a"
  );

  public DexItemBasedConstStringCanonicalizationTest(
      TestParameters parameters, boolean enableMinification) {
    super(parameters, enableMinification);
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue(
        "Only run JVM reference on CF runtimes",
        parameters.isCfRuntime() && !enableMinification);
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void test(SingleTestRunResult result, int expectedGetNameCount, int expectedConstString)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedGetNameCount, countGetName(mainMethod));
    assertEquals(
        expectedConstString,
        Streams.stream(mainMethod.iterateInstructions(
            i -> i.isConstString(JumboStringMode.ALLOW))).count());
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime() && !enableMinification);

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN, CanonicalizationTestClass.class)
            .setMinApi(parameters)
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 3, 0);

    result =
        testForD8()
            .release()
            .addProgramClasses(MAIN, CanonicalizationTestClass.class)
            .setMinApi(parameters)
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0, 1);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN, CanonicalizationTestClass.class)
            .addKeepMainRule(MAIN)
            .minification(enableMinification)
            .setMinApi(parameters)
            .addOptionsModification(this::configure)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(enableMinification ? RENAMED_OUTPUT : JAVA_OUTPUT);
    // Due to the different behavior regarding constant canonicalization.
    int expectedConstStringCount = parameters.isCfRuntime() ? 3 : 1;
    test(result, 0, expectedConstStringCount);
  }
}
