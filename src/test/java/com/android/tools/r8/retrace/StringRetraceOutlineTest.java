// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
public class StringRetraceOutlineTest extends TestBase {

  private static final String MAPPING =
      StringUtils.unixLines(
          "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
          "package.Class$$ExternalSyntheticOutline0 -> package.internal.X:",
          "# {'id':'sourceFile','fileName':'R8$$SyntheticClass'}",
          "# {'id':'com.android.tools.r8.synthesized'}",
          "    1:2:long package.$HASH$0.m(long,long,long):0:1 -> a",
          "    # {'id':'com.android.tools.r8.outline'}",
          "package.Class -> package.internal.Y:",
          "# {'id':'sourceFile','fileName':'FieldDefinition.java'}",
          "    1:6:void foo():21:26 -> a",
          "    7:7:void foo():0:0 -> a",
          "    # {'id':'com.android.tools.r8.outlineCallsite',"
              + "'positions':{'1':10,'2':11},"
              + "'outline':'Lpackage/internal/X;a(JJJ)J'}",
          "    8:9:void foo():38:39 -> a",
          "    10:10:void inlineeInOutline():1337:1337 -> a",
          "    10:10:void foo():42 -> a",
          "    11:11:void foo():44:44 -> a");

  private static final List<String> STACKTRACE =
      ImmutableList.of(
          "Error in something",
          "  at package.internal.X.a(SourceFile:1)",
          "  at package.internal.Y.a(SourceFile:7)");

  private static final List<String> EXPECTED =
      ImmutableList.of(
          "Error in something",
          "  at package.Class.inlineeInOutline(FieldDefinition.java:1337)",
          "  at package.Class.foo(FieldDefinition.java:42)");

  // TODO(b/305292991): These should all report EXPECTED.
  private static final List<String> UNEXPECTED =
      ImmutableList.of("Error in something", "  at package.Class.foo(FieldDefinition.java)");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public StringRetraceOutlineTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testStringRetraceAllFrames() {
    StringRetrace stringRetrace = getStringRetrace();
    RetraceStackFrameResultWithContext<String> result =
        stringRetrace.retrace(STACKTRACE, RetraceStackTraceContext.empty());
    assertEquals(EXPECTED, result.getResult());
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
    assertEquals(EXPECTED, retraced);
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
    assertEquals(UNEXPECTED, retraced);
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
      assertFalse(result.isAmbiguous());
      if (!result.isEmpty()) {
        assertEquals(1, result.size());
        result.get(0).forEach(retraced::add);
      }
    }
    assertEquals(UNEXPECTED, retraced);
  }

  @Test
  public void testStringRetraceSupplier() {
    StringRetrace stringRetrace = getStringRetrace();
    Iterator<String> iterator = STACKTRACE.iterator();
    List<String> retraced = new ArrayList<>();
    stringRetrace.retraceSupplier(() -> iterator.hasNext() ? iterator.next() : null, retraced::add);
    assertEquals(UNEXPECTED, retraced);
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
