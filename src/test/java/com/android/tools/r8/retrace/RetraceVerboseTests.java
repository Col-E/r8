// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.retrace.stacktraces.AmbiguousMethodVerboseStackTrace;
import com.android.tools.r8.retrace.stacktraces.AmbiguousWithSignatureVerboseStackTrace;
import com.android.tools.r8.retrace.stacktraces.FoundMethodVerboseStackTrace;
import com.android.tools.r8.retrace.stacktraces.StackTraceForTest;
import com.android.tools.r8.retrace.stacktraces.VerboseUnknownStackTrace;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceVerboseTests extends TestBase {

  @Parameters(name = "{0}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build());
  }

  public RetraceVerboseTests(TestParameters parameters) {}

  @Test
  public void testFoundMethod() {
    runRetraceTest(new FoundMethodVerboseStackTrace());
  }

  @Test
  public void testUnknownMethod() {
    runRetraceTest(new AmbiguousMethodVerboseStackTrace());
  }

  @Test
  public void testVerboseUnknownMethod() {
    runRetraceTest(new VerboseUnknownStackTrace());
  }

  @Test
  public void testAmbiguousMissingLineVerbose() {
    assumeTrue("b/169346455", false);
    runRetraceTest(new AmbiguousWithSignatureVerboseStackTrace());
  }

  private void runRetraceTest(StackTraceForTest stackTraceForTest) {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    RetraceCommand retraceCommand =
        RetraceCommand.builder(diagnosticsHandler)
            .setProguardMapProducer(ProguardMapProducer.fromString(stackTraceForTest.mapping()))
            .setStackTrace(stackTraceForTest.obfuscatedStackTrace())
            .setVerbose(true)
            .setRetracedStackTraceConsumer(
                retraced -> assertEquals(stackTraceForTest.retracedStackTrace(), retraced))
            .build();
    Retrace.run(retraceCommand);
  }
}
