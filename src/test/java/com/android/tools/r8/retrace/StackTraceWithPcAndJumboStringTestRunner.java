// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StackTraceWithPcAndJumboStringTestRunner extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StackTraceWithPcAndJumboStringTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  private Class<?> getTestClass() {
    return StackTraceWithPcAndJumboStringTest.class;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getTestClass())
        .run(parameters.getRuntime(), getTestClass())
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectStackTrace(stacktrace -> assertThat(stacktrace, isSame(getExpectedStackTrace())));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getTestClass())
        .noTreeShaking()
        .addKeepAttributeLineNumberTable()
        .addKeepMainRule(getTestClass())
        .setMinApi(parameters)
        .addOptionsModification(
            o -> {
              o.testing.forceJumboStringProcessing = true;
            })
        .run(parameters.getRuntime(), getTestClass())
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectStackTrace(stacktrace -> assertThat(stacktrace, isSame(getExpectedStackTrace())));
  }

  private StackTrace getExpectedStackTrace() {
    String className = getTestClass().getName();
    String sourceFile = getTestClass().getSimpleName() + ".java";
    return StackTrace.builder()
        .add(
            StackTraceLine.builder()
                .setClassName(className)
                .setFileName(sourceFile)
                .setMethodName("foo")
                .setLineNumber(10)
                .build())
        .add(
            StackTraceLine.builder()
                .setClassName(className)
                .setFileName(sourceFile)
                .setMethodName("bar")
                .setLineNumber(15)
                .build())
        .add(
            StackTraceLine.builder()
                .setClassName(className)
                .setFileName(sourceFile)
                .setMethodName("main")
                .setLineNumber(19)
                .build())
        .build();
  }
}
