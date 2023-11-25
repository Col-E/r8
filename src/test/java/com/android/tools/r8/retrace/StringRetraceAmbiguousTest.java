// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringRetraceAmbiguousTest extends TestBase {

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

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public StringRetraceAmbiguousTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testStringRetraceAllFrames() {
    StringRetrace stringRetrace = getStringRetrace();
    RetraceStackFrameResultWithContext<String> result =
        stringRetrace.retrace(STACKTRACE, RetraceStackTraceContext.empty());
    assertEquals(UNEXPECTED_UNAMBIGUOUS, result.getResult());
  }

  @Test
  public void testStringRetraceSingletonStack() {
    StringRetrace stringRetrace = getStringRetrace();
    RetraceStackTraceContext context = RetraceStackTraceContext.empty();
    List<String> retraced = new ArrayList<>();
    for (String line : STACKTRACE) {
      RetraceStackFrameResultWithContext<String> result =
          stringRetrace.retrace(Collections.singletonList(line), context);
      context = result.getContext();
      result.forEach(retraced::add);
    }
    assertEquals(UNEXPECTED_UNAMBIGUOUS, retraced);
  }

  @Test
  public void testStringRetraceByFrame() {
    StringRetrace stringRetrace = getStringRetrace();
    RetraceStackTraceContext context = RetraceStackTraceContext.empty();
    List<String> retraced = new ArrayList<>();
    for (String line : STACKTRACE) {
      RetraceStackFrameResultWithContext<String> result = stringRetrace.retrace(line, context);
      context = result.getContext();
      result.forEach(retraced::add);
    }
    assertEquals(UNEXPECTED_UNAMBIGUOUS, retraced);
  }

  @Test
  public void testStringRetraceByAmbiguousFrame() {
    StringRetrace stringRetrace = getStringRetrace();
    RetraceStackTraceContext context = RetraceStackTraceContext.empty();
    List<String> retraced = new ArrayList<>();
    for (String line : STACKTRACE) {
      RetraceStackFrameAmbiguousResultWithContext<String> result =
          stringRetrace.retraceFrame(line, context);
      context = result.getContext();
      if (result.isAmbiguous()) {
        for (int i = 0; i < result.size(); i++) {
          StringBuilder builder = new StringBuilder("alternative ").append(i).append(":\n");
          result.get(i).forEach(s -> builder.append(s).append('\n'));
          // Remove the last newline to stay consistent with "list of lines".
          String str = builder.toString();
          retraced.add(str.substring(0, str.length() - 1));
        }
      } else if (!result.isEmpty()) {
        result.get(0).forEach(retraced::add);
      }
    }
    assertEquals(UNEXPECTED_AMBIGUOUS, retraced);
  }

  @Test
  public void testStringRetraceSupplier() {
    StringRetrace stringRetrace = getStringRetrace();
    Iterator<String> iterator = STACKTRACE.iterator();
    List<String> retraced = new ArrayList<>();
    stringRetrace.retraceSupplier(() -> iterator.hasNext() ? iterator.next() : null, retraced::add);
    assertEquals(UNEXPECTED_UNAMBIGUOUS, retraced);
  }

  private static StringRetrace getStringRetrace() {
    ProguardMappingSupplier mappingSupplier =
        ProguardMappingSupplier.builder()
            .setProguardMapProducer(ProguardMapProducer.fromString(MAPPING))
            .build();

    StringRetrace stringRetrace =
        StringRetrace.create(RetraceOptions.builder().setMappingSupplier(mappingSupplier).build());
    return stringRetrace;
  }
}
