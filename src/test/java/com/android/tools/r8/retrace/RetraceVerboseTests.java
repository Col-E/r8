// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.retrace.stacktraces.FoundMethodVerboseStackTrace;
import com.android.tools.r8.retrace.stacktraces.StackTraceForTest;
import com.android.tools.r8.retrace.stacktraces.UnknownMethodVerboseStackTrace;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceVerboseTests extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetraceVerboseTests(TestParameters parameters) {}

  @Test
  public void testFoundMethod() {
    runRetraceTest(new FoundMethodVerboseStackTrace());
  }

  @Test
  public void testUnknownMethod() {
    runRetraceTest(new UnknownMethodVerboseStackTrace());
  }

  private TestDiagnosticMessagesImpl runRetraceTest(StackTraceForTest stackTraceForTest) {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    RetraceCommand retraceCommand =
        RetraceCommand.builder(diagnosticsHandler)
            .setProguardMapProducer(stackTraceForTest::mapping)
            .setStackTrace(stackTraceForTest.obfuscatedStackTrace())
            .setVerbose(true)
            .setRetracedStackTraceConsumer(
                retraced -> assertEquals(stackTraceForTest.retracedStackTrace(), retraced))
            .build();
    Retrace.run(retraceCommand);
    return diagnosticsHandler;
  }
}
