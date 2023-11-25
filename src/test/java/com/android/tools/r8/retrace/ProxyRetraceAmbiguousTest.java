// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProxyRetraceAmbiguousTest extends TestBase {

  private static final String MAPPING =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "package.Class$$ExternalSyntheticOutline0 -> package.internal.X:",
          "# {'id':'sourceFile','fileName':'R8$$SyntheticClass'}",
          "# {'id':'com.android.tools.r8.synthesized'}",
          "    1:2:long package.$HASH$0.m(long,long,long):0:1 -> a",
          "    # {'id':'com.android.tools.r8.synthesized'}",
          "package.Class -> package.internal.Y:",
          "# {'id':'sourceFile','fileName':'FieldDefinition.java'}",
          "    1:10:void foo():0:0 -> a",
          "    11:20:void bar():0:0 -> a");

  private static final List<String> STACKTRACE =
      ImmutableList.of(
          "Error in something",
          "  at package.internal.X.a(SourceFile:1)",
          "  at package.internal.Y.a(SourceFile)");

  // TODO(b/305292991): The result should be ambiguous with 'foo' and 'bar' frames.
  private static final List<String> UNEXPECTED_UNAMBIGUOUS =
      ImmutableList.of("Error in something", "  at package.Class.foo(FieldDefinition.java)");

  // TODO(b/305292991): The result should be ambiguous with 'foo' and 'bar' frames.
  private static final List<String> UNEXPECTED_AMBIGUOUS =
      ImmutableList.of(
          "Error in something",
          "alternative 0:\n  at package.Class.foo(FieldDefinition.java)",
          "alternative 1:\n  at package.Class.foo(FieldDefinition.java)");

  // TODO(b/305292991): The result should be ambiguous with 'foo' and 'bar' frames.
  private static final List<String> UNEXPECTED_AMBIGUOUS_JOINED =
      ImmutableList.of(
          "Error in something",
          "  at package.Class.foo(FieldDefinition.java)",
          "  at package.Class.foo(FieldDefinition.java)");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ProxyRetraceAmbiguousTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private List<String> joinResult(List<RetraceStackFrameAmbiguousResult<String>> result) {
    List<String> lines = new ArrayList<>();
    for (RetraceStackFrameAmbiguousResult<String> potentiallyAmbiguousFrame : result) {
      if (potentiallyAmbiguousFrame.isAmbiguous()) {
        for (int i = 0; i < potentiallyAmbiguousFrame.size(); i++) {
          StringBuilder builder = new StringBuilder("alternative ").append(i).append(":\n");
          potentiallyAmbiguousFrame.get(i).forEach(s -> builder.append(s).append('\n'));
          // Remove the last newline to stay consistent with "list of lines".
          String line = builder.toString();
          lines.add(line.substring(0, line.length() - 1));
        }
      } else if (!potentiallyAmbiguousFrame.isEmpty()) {
        potentiallyAmbiguousFrame.get(0).forEach(lines::add);
      }
    }
    return lines;
  }

  @Test
  public void testRetraceFullStack() {
    Retrace<String, StackTraceElementStringProxy> retrace = getRetraceProxy();
    RetraceStackTraceResult<String> result =
        retrace.retraceStackTrace(STACKTRACE, RetraceStackTraceContext.empty());
    assertEquals(UNEXPECTED_UNAMBIGUOUS, joinResult(result.getResult()));
  }

  @Test
  public void testRetraceSingletonStack() {
    Retrace<String, StackTraceElementStringProxy> retrace = getRetraceProxy();
    RetraceStackTraceContext context = RetraceStackTraceContext.empty();
    List<RetraceStackFrameAmbiguousResult<String>> retraced = new ArrayList<>();
    for (String line : STACKTRACE) {
      RetraceStackTraceResult<String> result =
          retrace.retraceStackTrace(Collections.singletonList(line), context);
      context = result.getContext();
      result.forEach(retraced::add);
    }
    assertEquals(UNEXPECTED_UNAMBIGUOUS, joinResult(retraced));
  }

  @Test
  public void testRetraceByLine() {
    Retrace<String, StackTraceElementStringProxy> retrace = getRetraceProxy();
    RetraceStackTraceContext context = RetraceStackTraceContext.empty();
    List<String> retraced = new ArrayList<>();
    for (String line : STACKTRACE) {
      RetraceStackFrameResultWithContext<String> result = retrace.retraceLine(line, context);
      context = result.getContext();
      result.forEach(retraced::add);
    }
    assertEquals(UNEXPECTED_AMBIGUOUS_JOINED, retraced);
  }

  @Test
  public void testRetraceByFrame() {
    Retrace<String, StackTraceElementStringProxy> retrace = getRetraceProxy();
    RetraceStackTraceContext context = RetraceStackTraceContext.empty();
    List<RetraceStackFrameAmbiguousResult<String>> retraced = new ArrayList<>();
    for (String line : STACKTRACE) {
      RetraceStackFrameAmbiguousResultWithContext<String> result =
          retrace.retraceFrame(line, context);
      context = result.getContext();
      retraced.add(result);
    }
    assertEquals(UNEXPECTED_AMBIGUOUS, joinResult(retraced));
  }

  @Test
  public void testRetraceFullStackParsed() {
    Retrace<String, StackTraceElementStringProxy> retrace = getRetraceProxy();
    RetraceStackTraceResult<String> result =
        retrace.retraceStackTraceParsed(getParsedStacktrace(), RetraceStackTraceContext.empty());
    assertEquals(UNEXPECTED_UNAMBIGUOUS, joinResult(result.getResult()));
  }

  @Test
  public void testRetraceSingletonStackParsed() {
    Retrace<String, StackTraceElementStringProxy> retrace = getRetraceProxy();
    RetraceStackTraceContext context = RetraceStackTraceContext.empty();
    List<RetraceStackFrameAmbiguousResult<String>> retraced = new ArrayList<>();
    for (StackTraceElementStringProxy line : getParsedStacktrace()) {
      RetraceStackTraceResult<String> result =
          retrace.retraceStackTraceParsed(Collections.singletonList(line), context);
      context = result.getContext();
      result.forEach(retraced::add);
    }
    assertEquals(UNEXPECTED_UNAMBIGUOUS, joinResult(retraced));
  }

  private static List<StackTraceElementStringProxy> getParsedStacktrace() {
    StackTraceLineParser<String, StackTraceElementStringProxy> parser = getParser();
    return ListUtils.map(STACKTRACE, parser::parse);
  }

  private static StackTraceLineParser<String, StackTraceElementStringProxy> getParser() {
    return StackTraceLineParser.createRegularExpressionParser(
        RetraceOptions.defaultRegularExpression());
  }

  private static Retrace<String, StackTraceElementStringProxy> getRetraceProxy() {
    ProguardMappingSupplier mappingSupplier =
        ProguardMappingSupplier.builder()
            .setProguardMapProducer(ProguardMapProducer.fromString(MAPPING))
            .build();
    Retrace<String, StackTraceElementStringProxy> retracer =
        new Retrace<>(getParser(), mappingSupplier, new DiagnosticsHandler() {}, false);
    return retracer;
  }
}
