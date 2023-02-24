// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.transformers.ClassFileTransformer.LineTranslation;
import com.android.tools.r8.transformers.MethodTransformer.MethodContext;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceCompanionWithPreambleTest extends TestBase {

  public enum Preamble {
    NONE,
    ONLY,
    BOTH
  }

  @Parameters(name = "{0}, preamble:{1}")
  public static List<Object[]> parameters() {
    return buildParameters(
        getTestParameters()
            .withDefaultRuntimes()
            .withApiLevel(AndroidApiLevel.B)
            .enableApiLevelsForCf()
            .build(),
        Preamble.values());
  }

  private final TestParameters parameters;
  private final Preamble preamble;

  public RetraceCompanionWithPreambleTest(TestParameters parameters, Preamble preamble) {
    this.parameters = parameters;
    this.preamble = preamble;
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getI())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .inspectStackTrace(RetraceCompanionWithPreambleTest::checkExpectedStackTrace);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .internalEnableMappingOutput()
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getI())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .inspectStackTrace(RetraceCompanionWithPreambleTest::checkExpectedStackTrace);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getI())
        .addKeepMainRule(Main.class)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkRunResult)
        .inspectStackTrace(RetraceCompanionWithPreambleTest::checkExpectedStackTrace);
  }

  private void checkRunResult(SingleTestRunResult<?> runResult) {
    runResult.assertFailureWithErrorThatMatches(containsString("Throw It!"));
  }

  private static void checkExpectedStackTrace(StackTrace stackTrace) {
    assertThat(
        stackTrace,
        isSameExceptForFileNameAndLineNumber(
            StackTrace.builder()
                .addWithoutFileNameAndLineNumber(Main.class, "doThrow")
                .addWithoutFileNameAndLineNumber(I.class, "foo")
                .addWithoutFileNameAndLineNumber(Main.class, "main")
                .build()));
  }

  private byte[] getI() throws IOException {
    return transformer(I.class)
        .setPredictiveLineNumbering(
            new LineTranslation() {
              boolean firstLine = true;

              @Override
              public int translate(MethodContext context, int line) {
                if (!context.getReference().getMethodName().equals("foo")) {
                  return line;
                }
                if (preamble == Preamble.NONE) {
                  return line;
                }
                if (preamble == Preamble.ONLY) {
                  return -1;
                }
                if (firstLine) {
                  firstLine = false;
                  return -1;
                }
                return line;
              }
            })
        .transform();
  }

  interface I {

    default void foo() {
      Main.doThrow();
      System.out.println("more stuff");
    }
  }

  static class A implements I {}

  public static class Main {

    public static void doThrow() {
      throw new RuntimeException("Throw It!");
    }

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
