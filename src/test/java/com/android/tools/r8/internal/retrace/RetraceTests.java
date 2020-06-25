// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.retrace;

import static com.android.tools.r8.retrace.Retrace.DEFAULT_REGULAR_EXPRESSION;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.internal.retrace.stacktraces.CronetStackTrace;
import com.android.tools.r8.internal.retrace.stacktraces.FinskyStackTrace;
import com.android.tools.r8.internal.retrace.stacktraces.VelvetStackTrace;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.android.tools.r8.retrace.stacktraces.StackTraceForTest;
import java.util.List;
import java.util.function.BiConsumer;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceTests extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetraceTests(TestParameters parameters) {}

  @Test
  public void testCronetStackTrace() {
    runRetraceTest(new CronetStackTrace());
  }

  @Test
  public void testFinskyStackTrace() {
    runRetraceTest(new FinskyStackTrace(), "(?:.*Finsky\\s+:\\s+\\[\\d+\\]\\s+%c\\.%m\\(%l\\):.*)");
  }

  @Test
  public void testVelvetStackTrace() {
    runRetraceTest(new VelvetStackTrace());
  }

  private TestDiagnosticMessagesImpl runRetraceTest(StackTraceForTest stackTraceForTest) {
    return runRetraceTest(stackTraceForTest, DEFAULT_REGULAR_EXPRESSION);
  }

  private TestDiagnosticMessagesImpl runRetraceTest(
      StackTraceForTest stackTraceForTest, String regularExpression) {
    return runRetraceTest(stackTraceForTest, regularExpression, TestCase::assertEquals);
  }

  private TestDiagnosticMessagesImpl runRetraceTest(
      StackTraceForTest stackTraceForTest,
      String regularExpression,
      BiConsumer<List<String>, List<String>> matcher) {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    RetraceCommand retraceCommand =
        RetraceCommand.builder(diagnosticsHandler)
            .setProguardMapProducer(stackTraceForTest::mapping)
            .setStackTrace(stackTraceForTest.obfuscatedStackTrace())
            .setRegularExpression(regularExpression)
            .setRetracedStackTraceConsumer(
                retraced -> matcher.accept(stackTraceForTest.retracedStackTrace(), retraced))
            .build();
    Retrace.run(retraceCommand);
    return diagnosticsHandler;
  }
}
