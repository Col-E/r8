// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StackTraceWithPcAndNoLineTableTestRunner extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StackTraceWithPcAndNoLineTableTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Class<?> getTestClass() {
    return StackTraceWithPcAndNoLineTableTest.class;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getTestClass())
        .run(parameters.getRuntime(), getTestClass())
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectStackTrace(
            stacktrace -> {
              assertThat(stacktrace, StackTrace.isSame(getExpectedStackTrace(true)));
            });
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getTestClass())
        .addKeepMainRule(getTestClass())
        .addKeepRules("-keep,allowshrinking,allowobfuscation class * { *; }")
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), getTestClass())
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectStackTrace(
            (stacktrace, inspector) -> {
              // The source file should be set to the default SourceFile when not kept.
              assertEquals(
                  "SourceFile",
                  inspector.clazz(getTestClass()).getDexProgramClass().sourceFile.toString());
              // TODO(b/202919530): The stack-trace should have line positions since they are
              //  essentially free when compiling for dex.
              if (parameters.isDexRuntime()
                  && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V8_1_0)) {
                assertThat(
                    stacktrace, StackTrace.isSameExceptForLineNumbers(getExpectedStackTrace(true)));
              } else {
                // Having stripped the line-number table, the raw stack will not have line info.
                assertThat(stacktrace, StackTrace.isSame(getExpectedStackTrace(false)));
              }
            });
  }

  private StackTrace getExpectedStackTrace(boolean withLines) {
    String className = getTestClass().getName();
    String sourceFile = getTestClass().getSimpleName() + ".java";
    return StackTrace.builder()
        .add(
            StackTraceLine.builder()
                .setClassName(className)
                .setFileName(sourceFile)
                .setMethodName("foo")
                .applyIf(
                    withLines,
                    b -> b.setLineNumber(10),
                    b -> {
                      if (parameters.isDexRuntime()) {
                        // TODO(b/255705077): Should not have position 0.
                        b.setLineNumber(0);
                      }
                    })
                .build())
        .add(
            StackTraceLine.builder()
                .setClassName(className)
                .setFileName(sourceFile)
                .setMethodName("bar")
                .applyIf(
                    withLines,
                    b -> b.setLineNumber(15),
                    b -> {
                      if (parameters.isDexRuntime()) {
                        // TODO(b/255705077): Should not have position 0.
                        b.setLineNumber(0);
                      }
                    })
                .build())
        .add(
            StackTraceLine.builder()
                .setClassName(className)
                .setFileName(sourceFile)
                .setMethodName("main")
                .applyIf(withLines, b -> b.setLineNumber(19))
                .build())
        .build();
  }
}
