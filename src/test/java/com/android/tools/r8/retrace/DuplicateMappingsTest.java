// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.PositionMatcher;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DuplicateMappingsTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DuplicateMappingsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testSourceFileName() {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    Retrace.run(
        RetraceCommand.builder(diagnosticsHandler)
            .setProguardMapProducer(
                () ->
                    StringUtils.lines(
                        "com.android.tools.r8.retrace.SourceFileTest$ClassWithCustomFileName ->"
                            + " com.android.tools.r8.retrace.a:",
                        "# {'id':'sourceFile','fileName':'foobarbaz.java'}",
                        "# {'id':'sourceFile','fileName':'foobarbaz2.java'}"))
            .setStackTrace(ImmutableList.of())
            .setRetracedStackTraceConsumer(
                strings -> {
                  // No need to do anything, we are just checking for diagnostics.
                })
            .build());
    diagnosticsHandler
        .assertWarningsCount(1)
        .assertWarningsMatch(
            allOf(
                DiagnosticsMatcher.diagnosticMessage(containsString("The mapping")),
                DiagnosticsMatcher.diagnosticMessage(
                    containsString("is not allowed in combination with")),
                DiagnosticsMatcher.diagnosticPosition(PositionMatcher.positionLine(3))));
  }
}
