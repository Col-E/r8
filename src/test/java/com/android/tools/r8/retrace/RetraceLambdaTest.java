// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isCompilerSynthesized;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceLambdaTest extends TestBase {

  private static final String JAVAC_LAMBDA_METHOD = "lambda$main$0";

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .apply(this::checkNoOutputSynthetics)
        .inspectStackTrace(RetraceLambdaTest::checkExpectedStackTrace);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .internalEnableMappingOutput()
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .apply(this::checkOneOutputSynthetic)
        .inspectStackTrace(RetraceLambdaTest::checkExpectedStackTrace);
  }

  @Test
  public void testEverythingInlined() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .inspectFailure(
            inspector ->
                assertEquals(parameters.isCfRuntime() ? 2 : 1, inspector.allClasses().size()))
        .inspectStackTrace(
            stackTrace -> {
              int frames = parameters.isCfRuntime() ? 2 : 1;
              checkRawStackTraceFrameCount(stackTrace, frames, "Expected everything to be inlined");
              checkExpectedStackTrace(stackTrace);
            });
  }

  @Test
  public void testNothingInlined() throws Exception {
    assumeTrue(
        "Skip R8/CF for min-api > 1 (R8/CF does not desugar)",
        parameters.isDexRuntime() || parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepPackageNamesRule(getClass().getPackage())
        .noTreeShaking()
        .addDontOptimize()
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .applyIf(
            parameters.isCfRuntime(), this::checkNoOutputSynthetics, this::checkOneOutputSynthetic)
        .inspectStackTrace(
            stackTrace -> {
              int frames = parameters.isCfRuntime() ? 3 : 5;
              checkRawStackTraceFrameCount(stackTrace, frames, "Expected nothing to be inlined");
              checkExpectedStackTrace(stackTrace);
            });
  }

  private void checkRunResult(SingleTestRunResult<?> runResult) {
    runResult.assertFailureWithErrorThatMatches(containsString("Hello World!"));
  }

  private void checkNoOutputSynthetics(SingleTestRunResult<?> runResult) throws IOException {
    checkOutputSynthetics(runResult, 0);
  }

  private void checkOneOutputSynthetic(SingleTestRunResult<?> runResult) throws IOException {
    checkOutputSynthetics(runResult, 1);
  }

  private void checkOutputSynthetics(SingleTestRunResult<?> runResult, int expectedSyntheticsCount)
      throws IOException {
    runResult.inspectFailure(
        inspector -> {
          Collection<ClassReference> inputs =
              ImmutableList.of(classFromClass(MyRunner.class), classFromClass(Main.class));
          for (FoundClassSubject clazz : inspector.allClasses()) {
            if (inputs.contains(clazz.getOriginalReference())) {
              assertThat(clazz, not(isCompilerSynthesized()));
            } else {
              assertThat(clazz, isCompilerSynthesized());
            }
          }
          assertEquals(inputs.size() + expectedSyntheticsCount, inspector.allClasses().size());
        });
  }

  private void checkRawStackTraceFrameCount(
      StackTrace stackTrace, int expectedFrames, String message) {
    int linesFromTest = 0;
    for (String line : stackTrace.getOriginalStderr().split("\n")) {
      if (line.trim().startsWith("at " + getClass().getPackage().getName())) {
        linesFromTest++;
      }
    }
    assertEquals(message + stackTrace.getOriginalStderr(), expectedFrames, linesFromTest);
  }

  private static void checkExpectedStackTrace(StackTrace stackTrace) {
    assertThat(
        stackTrace,
        isSameExceptForFileNameAndLineNumber(
            StackTrace.builder()
                .addWithoutFileNameAndLineNumber(Main.class, JAVAC_LAMBDA_METHOD)
                .addWithoutFileNameAndLineNumber(Main.class, "runIt")
                .addWithoutFileNameAndLineNumber(Main.class, "main")
                .build()));
  }

  public interface MyRunner {
    void run();
  }

  public static class Main {

    public static void runIt(MyRunner runner) {
      runner.run();
    }

    public static void main(String[] args) {
      if (args.length == 0) {
        runIt(
            () -> {
              throw new RuntimeException("Hello World!");
            });
      }
    }
  }
}
