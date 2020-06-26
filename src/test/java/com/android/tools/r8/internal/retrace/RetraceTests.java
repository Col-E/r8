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
import java.util.ArrayList;
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

  private static String REMAPPER_REGEX =
      "(?:.*?\\bat\\s+%c\\.%m\\s*\\(%S\\)\\s*)"
          + "|(?:(?:.*?[:\"]\\s+)?%c(?::.*)?)"
          + "|(?:.*?%t\\s+%c\\.%m\\s*\\(%a\\)\\s*)";
  private static String FINSKY_REGEX = "(?:.*Finsky\\s+:\\s+\\[\\d+\\]\\s+%c\\.%m\\(%l\\):.*)";
  private static String SMILEY_EMOJI = "\uD83D\uDE00";

  public RetraceTests(TestParameters parameters) {}

  @Test
  public void testCronetStackTrace() {
    runRetraceTest(new CronetStackTrace());
  }

  @Test
  public void testFinskyStackTrace() {
    runRetraceTest(new FinskyStackTrace(), FINSKY_REGEX);
  }

  @Test
  public void testCronetRemapperRegexpTest() {
    runRetraceTest(new CronetStackTrace(), REMAPPER_REGEX);
  }

  @Test
  public void testCronetAndFinskyStackTrace() {
    CronetStackTrace cronetStackTrace = new CronetStackTrace();
    FinskyStackTrace finskyStackTrace = new FinskyStackTrace();
    runRetraceTest(
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            ArrayList<String> obfuscated = new ArrayList<>();
            obfuscated.addAll(cronetStackTrace.obfuscatedStackTrace());
            obfuscated.addAll(finskyStackTrace.obfuscatedStackTrace());
            return obfuscated;
          }

          @Override
          public String mapping() {
            return cronetStackTrace.mapping();
          }

          @Override
          public List<String> retracedStackTrace() {
            ArrayList<String> retraced = new ArrayList<>();
            retraced.addAll(cronetStackTrace.retracedStackTrace());
            retraced.addAll(finskyStackTrace.retracedStackTrace());
            return retraced;
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        },
        FINSKY_REGEX + "|" + DEFAULT_REGULAR_EXPRESSION);
  }

  @Test
  public void testCronetAndFinskyStackTraceRemapperRegExp() {
    CronetStackTrace cronetStackTrace = new CronetStackTrace();
    FinskyStackTrace finskyStackTrace = new FinskyStackTrace();
    runRetraceTest(
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            ArrayList<String> obfuscated = new ArrayList<>();
            obfuscated.addAll(cronetStackTrace.obfuscatedStackTrace());
            obfuscated.addAll(finskyStackTrace.obfuscatedStackTrace());
            return obfuscated;
          }

          @Override
          public String mapping() {
            return cronetStackTrace.mapping();
          }

          @Override
          public List<String> retracedStackTrace() {
            ArrayList<String> retraced = new ArrayList<>();
            retraced.addAll(cronetStackTrace.retracedStackTrace());
            retraced.addAll(finskyStackTrace.retracedStackTrace());
            return retraced;
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        },
        FINSKY_REGEX + "|" + REMAPPER_REGEX);
  }

  @Test
  public void testVelvetStackTrace() {
    runRetraceTest(new VelvetStackTrace());
  }

  @Test
  public void testNonAscii() {
    CronetStackTrace cronetStackTrace = new CronetStackTrace();
    runRetraceTest(
        new StackTraceForTest() {
          @Override
          public List<String> obfuscatedStackTrace() {
            ArrayList<String> smileyObf = new ArrayList<>();
            smileyObf.add(SMILEY_EMOJI);
            smileyObf.addAll(cronetStackTrace.obfuscatedStackTrace());
            return smileyObf;
          }

          @Override
          public String mapping() {
            return cronetStackTrace.mapping();
          }

          @Override
          public List<String> retracedStackTrace() {
            ArrayList<String> smileyObf = new ArrayList<>();
            smileyObf.add(SMILEY_EMOJI);
            smileyObf.addAll(cronetStackTrace.retracedStackTrace());
            return smileyObf;
          }

          @Override
          public int expectedWarnings() {
            return 0;
          }
        });
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
