// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.retrace.Retrace.DEFAULT_REGULAR_EXPRESSION;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.retrace.internal.RetraceAbortException;
import com.android.tools.r8.retrace.internal.RetracerImpl;
import com.android.tools.r8.retrace.stacktraces.ActualBotStackTraceBase;
import com.android.tools.r8.retrace.stacktraces.ActualIdentityStackTrace;
import com.android.tools.r8.retrace.stacktraces.ActualRetraceBotStackTrace;
import com.android.tools.r8.retrace.stacktraces.AmbiguousMissingLineStackTrace;
import com.android.tools.r8.retrace.stacktraces.AmbiguousStackTrace;
import com.android.tools.r8.retrace.stacktraces.AmbiguousWithMultipleLineMappingsStackTrace;
import com.android.tools.r8.retrace.stacktraces.AmbiguousWithSignatureNonVerboseStackTrace;
import com.android.tools.r8.retrace.stacktraces.CircularReferenceStackTrace;
import com.android.tools.r8.retrace.stacktraces.FileNameExtensionStackTrace;
import com.android.tools.r8.retrace.stacktraces.InlineFileNameStackTrace;
import com.android.tools.r8.retrace.stacktraces.InlineFileNameWithInnerClassesStackTrace;
import com.android.tools.r8.retrace.stacktraces.InlineNoLineNumberStackTrace;
import com.android.tools.r8.retrace.stacktraces.InlineSourceFileContextStackTrace;
import com.android.tools.r8.retrace.stacktraces.InlineWithLineNumbersStackTrace;
import com.android.tools.r8.retrace.stacktraces.InvalidStackTrace;
import com.android.tools.r8.retrace.stacktraces.MemberFieldOverlapStackTrace;
import com.android.tools.r8.retrace.stacktraces.NamedModuleStackTrace;
import com.android.tools.r8.retrace.stacktraces.NullStackTrace;
import com.android.tools.r8.retrace.stacktraces.ObfucatedExceptionClassStackTrace;
import com.android.tools.r8.retrace.stacktraces.ObfuscatedRangeToSingleLineStackTrace;
import com.android.tools.r8.retrace.stacktraces.RetraceAssertionErrorStackTrace;
import com.android.tools.r8.retrace.stacktraces.StackTraceForTest;
import com.android.tools.r8.retrace.stacktraces.SuppressedStackTrace;
import com.android.tools.r8.retrace.stacktraces.UnknownSourceStackTrace;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceTests extends TestBase {

  @Parameters(name = "{0}, use regular expression: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  private final boolean useRegExpParsing;

  public RetraceTests(TestParameters parameters, boolean useRegExpParsing) {
    this.useRegExpParsing = useRegExpParsing;
  }

  @Test
  public void testCanMapExceptionClass() {
    runRetraceTest(new ObfucatedExceptionClassStackTrace());
  }

  @Test
  public void testSuppressedStackTrace() {
    runRetraceTest(new SuppressedStackTrace());
  }

  @Test
  public void testFileNameStackTrace() {
    runRetraceTest(new FileNameExtensionStackTrace());
  }

  @Test
  public void testInlineFileNameStackTrace() {
    runRetraceTest(new InlineFileNameStackTrace());
  }

  @Test
  public void testInlineFileNameWithInnerClassesStackTrace() {
    runRetraceTest(new InlineFileNameWithInnerClassesStackTrace());
  }

  @Test
  public void testNullLineTrace() {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    NullStackTrace nullStackTrace = new NullStackTrace();
    RetraceCommand retraceCommand =
        RetraceCommand.builder(diagnosticsHandler)
            .setProguardMapProducer(nullStackTrace::mapping)
            .setStackTrace(nullStackTrace.obfuscatedStackTrace())
            .setRetracedStackTraceConsumer(retraced -> fail())
            .build();
    try {
      Retrace.run(retraceCommand);
      fail();
    } catch (RetraceAbortException e) {
      diagnosticsHandler.assertOnlyErrors();
      diagnosticsHandler.assertErrorsCount(1);
      assertThat(
          diagnosticsHandler.getErrors().get(0).getDiagnosticMessage(),
          containsString("The stack trace line is <null>"));
    }
  }

  @Test
  public void testInvalidStackTraceLineWarnings() {
    InvalidStackTrace invalidStackTraceTest = new InvalidStackTrace();
    runRetraceTest(invalidStackTraceTest).assertNoMessages();
  }

  @Test
  public void testAssertionErrorInRetrace() {
    runRetraceTest(new RetraceAssertionErrorStackTrace());
  }

  @Test
  public void testActualStackTraces() {
    List<ActualBotStackTraceBase> stackTraces =
        ImmutableList.of(new ActualIdentityStackTrace(), new ActualRetraceBotStackTrace());
    for (ActualBotStackTraceBase stackTrace : stackTraces) {
      runRetraceTest(stackTrace)
          .assertWarningsCount(useRegExpParsing ? 0 : stackTrace.expectedWarnings());
    }
  }

  @Test
  public void testAmbiguousStackTrace() {
    runRetraceTest(new AmbiguousStackTrace());
  }

  @Test
  public void testAmbiguousMissingLineStackTrace() {
    runRetraceTest(new AmbiguousMissingLineStackTrace());
  }

  @Test
  public void testAmbiguousMissingLineNotVerbose() {
    runRetraceTest(new AmbiguousWithSignatureNonVerboseStackTrace());
  }

  @Test
  public void testAmbiguousMultipleMappingsTest() {
    runRetraceTest(new AmbiguousWithMultipleLineMappingsStackTrace());
  }

  @Test
  public void testInliningWithLineNumbers() {
    runRetraceTest(new InlineWithLineNumbersStackTrace());
  }

  @Test
  public void testInliningNoLineNumberInfoStackTraces() {
    runRetraceTest(new InlineNoLineNumberStackTrace());
  }

  @Test
  public void testCircularReferenceStackTrace() {
    // Proguard retrace (and therefore the default regular expression) will not retrace circular
    // reference exceptions.
    assumeFalse(useRegExpParsing);
    runRetraceTest(new CircularReferenceStackTrace());
  }

  @Test
  public void testObfuscatedRangeToSingleLine() {
    runRetraceTest(new ObfuscatedRangeToSingleLineStackTrace());
  }

  @Test
  @Ignore("b/170293908")
  public void testBootLoaderAndNamedModulesStackTrace() {
    assumeFalse(useRegExpParsing);
    runRetraceTest(new NamedModuleStackTrace());
  }

  @Test
  public void testUnknownSourceStackTrace() {
    runRetraceTest(new UnknownSourceStackTrace());
  }

  @Test
  public void testInlineSourceFileContext() {
    runRetraceTest(new InlineSourceFileContextStackTrace());
  }

  @Test
  public void testMemberFieldOverlapStackTrace() throws Exception {
    MemberFieldOverlapStackTrace stackTraceForTest = new MemberFieldOverlapStackTrace();
    runRetraceTest(stackTraceForTest);
    inspectRetraceTest(stackTraceForTest, stackTraceForTest::inspectField);
  }

  private void inspectRetraceTest(
      StackTraceForTest stackTraceForTest, Consumer<Retracer> inspection) throws Exception {
    inspection.accept(
        RetracerImpl.create(stackTraceForTest::mapping, new TestDiagnosticMessagesImpl()));
  }

  private TestDiagnosticMessagesImpl runRetraceTest(StackTraceForTest stackTraceForTest) {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    RetraceCommand retraceCommand =
        RetraceCommand.builder(diagnosticsHandler)
            .setProguardMapProducer(stackTraceForTest::mapping)
            .setStackTrace(stackTraceForTest.obfuscatedStackTrace())
            .setRegularExpression(useRegExpParsing ? DEFAULT_REGULAR_EXPRESSION : null)
            .setRetracedStackTraceConsumer(
                retraced -> assertEquals(stackTraceForTest.retracedStackTrace(), retraced))
            .build();
    Retrace.run(retraceCommand);
    return diagnosticsHandler;
  }
}
