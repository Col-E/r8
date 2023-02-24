// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LambdaInStacktraceTest extends TestBase {

  static final String fileName = "LambdaInStacktraceTest.java";

  static final String EXPECTED_JAVAC =
      StringUtils.lines(
          "getStacktraceWithFileNames(" + fileName + ")",
          "lambda$main$0(" + fileName + ")",
          "main(" + fileName + ")",
          "getStacktraceWithFileNames(" + fileName + ")",
          "lambda$main$1(" + fileName + ")",
          "main(" + fileName + ")");

  // TODO(b/187491007): The "call" frame should have a file name.
  static final String EXPECTED_D8 =
      StringUtils.lines(
          "getStacktraceWithFileNames(" + fileName + ")",
          "lambda$main$0(" + fileName + ")",
          "call(D8$$SyntheticClass)",
          "main(" + fileName + ")",
          "getStacktraceWithFileNames(" + fileName + ")",
          "lambda$main$1(" + fileName + ")",
          "call(D8$$SyntheticClass)",
          "main(" + fileName + ")");

  static final String EXPECTED_D8_ANDROID_O =
      StringUtils.lines(
          "getStacktraceWithFileNames(" + fileName + ")",
          "lambda$main$0(" + fileName + ")",
          "call(NULL)",
          "main(" + fileName + ")",
          "getStacktraceWithFileNames(" + fileName + ")",
          "lambda$main$1(" + fileName + ")",
          "call(NULL)",
          "main(" + fileName + ")");

  private final TestParameters parameters;
  private final boolean isAndroidOOrLater;
  private final boolean isDalvik;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public LambdaInStacktraceTest(TestParameters parameters) {
    this.parameters = parameters;
    isDalvik = parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik();
    isAndroidOOrLater =
        parameters.isDexRuntime()
            && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V8_1_0);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(LambdaInStacktraceTest.class)
        .run(parameters.getRuntime(), TestRunner.class, Boolean.toString(false))
        .assertSuccess()
        .assertSuccessWithOutput(EXPECTED_JAVAC);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(LambdaInStacktraceTest.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestRunner.class, Boolean.toString(isDalvik))
        .assertSuccessWithOutput(isAndroidOOrLater ? EXPECTED_D8_ANDROID_O : EXPECTED_D8);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.getRuntime().isDex());
    String stdout =
        testForR8(parameters.getBackend())
            .addInnerClasses(LambdaInStacktraceTest.class)
            .setMinApi(parameters)
            .addKeepMainRule(TestRunner.class)
            .addKeepAttributeSourceFile()
            .addKeepRules("-renamesourcefileattribute SourceFile")
            .noTreeShaking()
            .addDontOptimize()
            .run(parameters.getRuntime(), TestRunner.class, Boolean.toString(isDalvik))
            .assertSuccess()
            .getStdOut();
    assertTrue(
        StringUtils.splitLines(stdout).stream()
            .allMatch(
                s -> {
                  if (parameters
                      .getApiLevel()
                      .isGreaterThanOrEqualTo(apiLevelWithPcAsLineNumberSupport())) {
                    return s.contains("(NULL)");
                  } else if (isAndroidOOrLater) {
                    // On VMs with native support, no line info results in no source file printing.
                    // TODO(b/260384637): Create debug info for such methods to avoid this.
                    return s.equals("main(NULL)")
                        || (!s.startsWith("main") && s.contains("(SourceFile)"));
                  } else {
                    return s.contains("(SourceFile)");
                  }
                }));
  }

  static class TestRunner {

    public static List<String> getStacktraceWithFileNames(boolean isDalvik) {
      Throwable stackTrace = new RuntimeException().fillInStackTrace();

      // Dalvik has an additional "main(NativeStart.java)" bottom frame.
      List<String> frames = new ArrayList<>();
      for (int i = 0; i < stackTrace.getStackTrace().length - (isDalvik ? 1 : 0); i++) {
        StackTraceElement stackTraceElement = stackTrace.getStackTrace()[i];
        String fileName = stackTraceElement.getFileName();
        frames.add(
            stackTraceElement.getMethodName() + "(" + (fileName != null ? fileName : "NULL") + ")");
      }
      return frames;
    }

    public static void main(String[] args) throws Exception {
      boolean isDalvik = Boolean.parseBoolean(args[0]);
      Callable<List<String>> callable = () -> getStacktraceWithFileNames(isDalvik);
      System.out.println(String.join(System.lineSeparator(), callable.call()));

      Callable<Callable<List<String>>> callableNested =
          () -> () -> getStacktraceWithFileNames(isDalvik);
      System.out.println(String.join(System.lineSeparator(), callableNested.call().call()));
    }
  }
}
