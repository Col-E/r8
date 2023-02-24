// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.testing;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileName;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StackTraceTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public StackTraceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvmStackTrace() {
    String stderr =
        StringUtils.join(
            "\n",
            ImmutableList.of(
                "Exception in thread \"main\" java.lang.RuntimeException",
                "\tat com.example.A.method2(Test.java:30)",
                "\tat com.example.A.method1(Test.java:20)",
                "\tat com.example.Main.main(Test.java:10)"));
    checkStackTrace(StackTrace.extractFromJvm(stderr));
  }

  @Test
  public void testArtStackTrace1() {
    String stderr =
        StringUtils.join(
            "\n",
            "dex2oat I 10-30 11:41:40 232588 232588 dex2oat.cc:3108]"
                + " /usr/local/prj/r8-public/ws1/tools/linux/art/bin/../bin/dex2oat"
                + " --instruction-set=x86_64"
                + " --instruction-set-features=ssse3,sse4.1,sse4.2,-avx,-avx2,popcnt --runtime-arg"
                + " -Xnorelocate --host"
                + " --boot-image=/usr/local/prj/r8-public/ws1/tools/linux/art/bin/../framework/core.art"
                + " --dex-file=/tmp/junit5857866495600860150/junit14865793933637294589/out.zip"
                + " --output-vdex-fd=7 --oat-fd=8"
                + " --oat-location=/tmp/junit5857866495600860150/junit14865793933637294589/oat/x86_64/out.odex"
                + " --compiler-filter=quicken --class-loader-context=PCL[]\n",
            "dex2oat I 10-30 11:41:40 232588 232588 dex2oat.cc:2808] dex2oat took 94.860ms"
                + " (71.941ms cpu) (threads: 72) arena alloc=3KB (3312B) java alloc=32KB (32800B)"
                + " native alloc=440KB (450720B) free=9MB (9539424B)",
            "Exception in thread \"main\" java.lang.RuntimeException",
            "\tat com.example.A.method2(Test.java:30)",
            "\tat com.example.A.method1(Test.java:20)",
            "\tat com.example.Main.main(Test.java:10)");
    checkStackTrace(StackTrace.extractFromArt(stderr, DexVm.ART_5_1_1_HOST));
  }

  @Test
  public void testArtStackTrace2() {
    String stderr =
        StringUtils.join(
            "\n",
            "dex2oat I 10-30 11:41:40 232588 232588 dex2oat.cc:3108]"
                + " /usr/local/prj/r8-public/ws1/tools/linux/art/bin/../bin/dex2oat"
                + " --instruction-set=x86_64"
                + " --instruction-set-features=ssse3,sse4.1,sse4.2,-avx,-avx2,popcnt --runtime-arg"
                + " -Xnorelocate --host"
                + " --boot-image=/usr/local/prj/r8-public/ws1/tools/linux/art/bin/../framework/core.art"
                + " --dex-file=/tmp/junit5857866495600860150/junit14865793933637294589/out.zip"
                + " --output-vdex-fd=7 --oat-fd=8"
                + " --oat-location=/tmp/junit5857866495600860150/junit14865793933637294589/oat/x86_64/out.odex"
                + " --compiler-filter=quicken --class-loader-context=PCL[]\n",
            "Exception in thread \"main\" java.lang.RuntimeException",
            "\tat com.example.A.method2(Test.java:30)",
            "\tat com.example.A.method1(Test.java:20)",
            "\tat com.example.Main.main(Test.java:10)",
            "dex2oat I 10-30 11:41:40 232588 232588 dex2oat.cc:2808] dex2oat took 94.860ms"
                + " (71.941ms cpu) (threads: 72) arena alloc=3KB (3312B) java alloc=32KB (32800B)"
                + " native alloc=440KB (450720B) free=9MB (9539424B)");
    checkStackTrace(StackTrace.extractFromArt(stderr, DexVm.ART_5_1_1_HOST));
  }

  private void checkStackTrace(StackTrace stackTrace) {
    assertThat(
        stackTrace,
        isSame(
            StackTrace.builder()
                .add(
                    StackTraceLine.builder()
                        .setClassName("com.example.A")
                        .setMethodName("method2")
                        .setFileName("Test.java")
                        .setLineNumber(30)
                        .build())
                .add(
                    StackTraceLine.builder()
                        .setClassName("com.example.A")
                        .setMethodName("method1")
                        .setFileName("Test.java")
                        .setLineNumber(20)
                        .build())
                .add(
                    StackTraceLine.builder()
                        .setClassName("com.example.Main")
                        .setMethodName("main")
                        .setFileName("Test.java")
                        .setLineNumber(10)
                        .build())
                .build()));

    assertThat(
        stackTrace,
        isSameExceptForFileName(
            StackTrace.builder()
                .add(
                    StackTraceLine.builder()
                        .setClassName("com.example.A")
                        .setMethodName("method2")
                        .setLineNumber(30)
                        .build())
                .add(
                    StackTraceLine.builder()
                        .setClassName("com.example.A")
                        .setMethodName("method1")
                        .setLineNumber(20)
                        .build())
                .add(
                    StackTraceLine.builder()
                        .setClassName("com.example.Main")
                        .setMethodName("main")
                        .setLineNumber(10)
                        .build())
                .build()));

    assertThat(
        stackTrace,
        isSameExceptForFileNameAndLineNumber(
            StackTrace.builder()
                .add(
                    StackTraceLine.builder()
                        .setClassName("com.example.A")
                        .setMethodName("method2")
                        .build())
                .add(
                    StackTraceLine.builder()
                        .setClassName("com.example.A")
                        .setMethodName("method1")
                        .build())
                .add(
                    StackTraceLine.builder()
                        .setClassName("com.example.Main")
                        .setMethodName("main")
                        .build())
                .build()));
  }

  @Test
  public void testJvmStackTraceFromRunning() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(StackTraceTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertFailure()
        .inspectStackTrace(this::checkStackTraceFromRunning);
  }

  @Test
  public void testArtStackTraceFromRunning() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addInnerClasses(StackTraceTest.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertFailure()
        .inspectStackTrace(this::checkStackTraceFromRunning);
  }

  private void checkStackTraceFromRunning(StackTrace stackTrace) {
    assertThat(
        stackTrace,
        isSameExceptForFileNameAndLineNumber(
            StackTrace.builder()
                .addWithoutFileNameAndLineNumber(A.class, "method2")
                .addWithoutFileNameAndLineNumber(A.class, "method1")
                .addWithoutFileNameAndLineNumber(Main.class, "main")
                .build()));
  }

  public static class Main {
    public static void main(String[] args) {
      new A().method1();
    }
  }

  static class A {
    public void method1() {
      method2();
    }

    public void method2() {
      throw new RuntimeException();
    }
  }
}
