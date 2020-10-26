// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.retrace.Retrace.DEFAULT_REGULAR_EXPRESSION;
import static junit.framework.TestCase.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.retrace.stacktraces.AmbiguousMethodVerboseStackTrace;
import com.android.tools.r8.retrace.stacktraces.AmbiguousWithSignatureVerboseStackTrace;
import com.android.tools.r8.retrace.stacktraces.FoundMethodVerboseStackTrace;
import com.android.tools.r8.retrace.stacktraces.StackTraceForTest;
import com.android.tools.r8.retrace.stacktraces.VerboseUnknownStackTrace;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceVerboseTests extends TestBase {

  @Parameters(name = "{0}, use regular expression: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  private final boolean useRegExpParsing;

  public RetraceVerboseTests(TestParameters parameters, boolean useRegExpParsing) {
    this.useRegExpParsing = useRegExpParsing;
  }

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
    // TODO(b/169346455): Enable when separated parser.
    assumeFalse(useRegExpParsing);
    runRetraceTest(new AmbiguousWithSignatureVerboseStackTrace());
  }

  private TestDiagnosticMessagesImpl runRetraceTest(StackTraceForTest stackTraceForTest) {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    RetraceCommand retraceCommand =
        RetraceCommand.builder(diagnosticsHandler)
            .setProguardMapProducer(stackTraceForTest::mapping)
            .setStackTrace(stackTraceForTest.obfuscatedStackTrace())
            .setRegularExpression(useRegExpParsing ? DEFAULT_REGULAR_EXPRESSION : null)
            .setVerbose(true)
            .setRetracedStackTraceConsumer(
                retraced -> assertEquals(stackTraceForTest.retracedStackTrace(), retraced))
            .build();
    Retrace.run(retraceCommand);
    return diagnosticsHandler;
  }
}
