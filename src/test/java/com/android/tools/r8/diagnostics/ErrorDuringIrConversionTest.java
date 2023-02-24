// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.diagnostics;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import org.hamcrest.Matcher;
import org.hamcrest.core.IsAnything;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ErrorDuringIrConversionTest extends TestBase {

  static final Origin ORIGIN =
      new Origin(Origin.root()) {
        @Override
        public String part() {
          return "<test-origin>";
        }
      };

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ErrorDuringIrConversionTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private ThrowableConsumer<D8TestBuilder> addTestClassWithOrigin() {
    return b ->
        b.getBuilder().addClassProgramData(ToolHelper.getClassAsBytes(TestClass.class), ORIGIN);
  }

  private void checkCompilationFailedException(
      CompilationFailedException e, Matcher<String> messageMatcher, Matcher<String> stackMatcher) {
    // Check that the failure exception exiting the compiler contains origin info in the message.
    assertThat(e.getMessage(), messageMatcher);
    StringWriter writer = new StringWriter();
    e.printStackTrace(new PrintWriter(writer));
    String fullStackTrace = writer.toString();
    // Extract the top cause stack.
    int topStackTraceEnd = fullStackTrace.indexOf("Caused by:");
    String topStackTrace = fullStackTrace.substring(0, topStackTraceEnd);
    String restStackTrace = fullStackTrace.substring(topStackTraceEnd);
    // Check that top stack trace always has the version marker.
    assertThat(topStackTrace, containsString("fakeStackEntry"));
    // Check that top stack has the D8 entry (from tests the non-renamed entry is ToolHelper.runX).
    assertThat(topStackTrace, containsString("com.android.tools.r8.ToolHelper.run"));
    // Check that the stack trace always has the suppressed info.
    assertThat(restStackTrace, containsString(StringUtils.LINE_SEPARATOR + "\tSuppressed:"));
    // Custom test checks.
    assertThat(restStackTrace, stackMatcher);
  }

  private static void throwNPE() {
    throw new NullPointerException("A test NPE");
  }

  @Test
  public void testNPE() {
    try {
      testForD8()
          .apply(addTestClassWithOrigin())
          .addOptionsModification(
              options -> options.testing.hookInIrConversion = ErrorDuringIrConversionTest::throwNPE)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                // Check that the error is reported as an error to the diagnostics handler.
                diagnostics
                    .assertOnlyErrors()
                    .assertErrorsMatch(
                        allOf(
                            diagnosticOrigin(ORIGIN),
                            diagnosticException(NullPointerException.class),
                            diagnosticMessage(containsString("A test NPE"))));
              });
    } catch (CompilationFailedException e) {
      checkCompilationFailedException(
          e, containsString(ORIGIN.toString()), containsString("throwNPE"));
      return;
    }
    fail("Expected compilation to fail");
  }

  @Test
  public void testFatalError() {
    try {
      testForD8()
          .apply(addTestClassWithOrigin())
          .addOptionsModification(
              options ->
                  options.testing.hookInIrConversion =
                      () -> options.reporter.fatalError("My Fatal Error!"))
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                // Check that the error is reported as an error to the diagnostics handler.
                diagnostics.assertOnlyErrors();
                diagnostics.assertErrorsMatch(
                    allOf(
                        diagnosticType(StringDiagnostic.class),
                        diagnosticMessage(containsString("My Fatal Error")),
                        // The fatal error is not given an origin, so it can't provide it.
                        // Note: This could be fixed by delaying reporting and associate the info
                        //  at the top-level handler. It would require mangling of the diagnostic,
                        //  so maybe not that elegant.
                        diagnosticOrigin(Origin.unknown())));
              });
    } catch (CompilationFailedException e) {
      checkCompilationFailedException(e, containsString(ORIGIN.toString()), new IsAnything<>());
      return;
    }
    fail("Expected compilation to fail");
  }

  private static void reportErrors(Reporter reporter) {
    reporter.error("FOO!");
    reporter.error("BAR!");
    reporter.error("BAZ!");
  }

  @Test
  public void testThreeErrors() {
    AtomicBoolean doError = new AtomicBoolean(true);
    try {
      testForD8()
          .apply(addTestClassWithOrigin())
          .addOptionsModification(
              options ->
                  options.testing.hookInIrConversion =
                      () -> {
                        // Ensure that the errors are reported just once as IR conversion is
                        // threaded.
                        if (doError.getAndSet(false)) {
                          reportErrors(options.reporter);
                        }
                      })
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                // Check that the error is reported as an error to the diagnostics handler.
                diagnostics
                    .assertOnlyErrors()
                    .assertErrorsCount(3)
                    .assertAllErrorsMatch(
                        allOf(
                            diagnosticOrigin(Origin.unknown()),
                            diagnosticType(StringDiagnostic.class),
                            diagnosticMessage(
                                anyOf(
                                    containsString("FOO!"),
                                    containsString("BAR!"),
                                    containsString("BAZ!")))));
              });
    } catch (CompilationFailedException e) {
      checkCompilationFailedException(
          e,
          // There may be no fail-if-error barrier inside any origin association, thus only the
          // top level message can be expected here.
          containsString("Compilation failed to complete"),
          // The stack trace must contain the reportErrors frame for the hook above, and one
          // of the error messages.
          allOf(
              containsString("reportErrors"),
              anyOf(containsString("FOO!"), containsString("BAR!"), containsString("BAZ!"))));
      return;
    }
    fail("Expected compilation to fail");
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
