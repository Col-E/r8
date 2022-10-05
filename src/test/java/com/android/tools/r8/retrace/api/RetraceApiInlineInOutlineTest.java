// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedSingleFrame;
import com.android.tools.r8.retrace.Retracer;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiInlineInOutlineTest extends RetraceApiTestBase {

  public RetraceApiInlineInOutlineTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final ClassReference outlineRenamed = Reference.classFromTypeName("a");
    private final ClassReference callsiteOriginal = Reference.classFromTypeName("some.Class");
    private final ClassReference callsiteRenamed = Reference.classFromTypeName("b");

    private final String mapping =
        "# { id: 'com.android.tools.r8.mapping', version: '2.0' }\n"
            + "outline.Class -> "
            + outlineRenamed.getTypeName()
            + ":\n"
            + "  1:2:int some.inlinee():75:76 -> a\n"
            + "  1:2:int outline():0 -> a\n"
            + "# { 'id':'com.android.tools.r8.outline' }\n"
            + callsiteOriginal.getTypeName()
            + " -> "
            + callsiteRenamed.getTypeName()
            + ":\n"
            + "  1:1:void foo.bar.Baz.qux():42:42 -> s\n"
            + "  5:5:int foo.bar.baz.outlineCaller(int):98:98 -> s\n"
            + "  5:5:int outlineCaller(int):24 -> s\n"
            + "  27:27:int outlineCaller(int):0:0 -> s\n"
            + "# { 'id':'com.android.tools.r8.outlineCallsite', "
            + "    'positions': { '1': 4, '2': 5 } }\n";

    @Test
    public void test() {
      ProguardMappingSupplier mappingSupplier =
          ProguardMappingSupplier.builder()
              .setProguardMapProducer(ProguardMapProducer.fromString(mapping))
              .build();
      Retracer retracer = mappingSupplier.createRetracer(new DiagnosticsHandler() {});
      List<RetraceFrameElement> outlineRetraced =
          retracer
              .retraceFrame(
                  RetraceStackTraceContext.empty(),
                  OptionalInt.of(2),
                  Reference.methodFromDescriptor(outlineRenamed, "a", "()I"))
              .stream()
              .collect(Collectors.toList());
      // The retrace result should not be ambiguous or empty.
      assertEquals(1, outlineRetraced.size());
      RetraceFrameElement retraceFrameElement = outlineRetraced.get(0);

      // Check that visiting all frames report the outline.
      List<RetracedMethodReference> allMethodReferences =
          retraceFrameElement.stream()
              .map(RetracedSingleFrame::getMethodReference)
              .collect(Collectors.toList());
      assertEquals(2, allMethodReferences.size());
      assertEquals(76, allMethodReferences.get(0).getOriginalPositionOrDefault(2));
      assertEquals(0, allMethodReferences.get(1).getOriginalPositionOrDefault(2));

      // Check that visiting rewritten frames will not report anything.
      List<RetracedMethodReference> rewrittenReferences =
          retraceFrameElement
              .streamRewritten(RetraceStackTraceContext.empty())
              .map(RetracedSingleFrame::getMethodReference)
              .collect(Collectors.toList());
      assertEquals(1, rewrittenReferences.size());
      assertEquals(76, allMethodReferences.get(0).getOriginalPositionOrDefault(2));

      // Retrace the outline position
      RetraceStackTraceContext context = retraceFrameElement.getRetraceStackTraceContext();
      List<RetraceFrameElement> retraceOutlineCallee =
          retracer
              .retraceFrame(
                  context,
                  OptionalInt.of(27),
                  Reference.methodFromDescriptor(callsiteRenamed, "s", "(I)I"))
              .stream()
              .collect(Collectors.toList());
      assertEquals(1, retraceOutlineCallee.size());

      List<RetracedMethodReference> outlineCallSiteFrames =
          retraceOutlineCallee.get(0).stream()
              .map(RetracedSingleFrame::getMethodReference)
              .collect(Collectors.toList());
      assertEquals(2, outlineCallSiteFrames.size());
      assertEquals(98, outlineCallSiteFrames.get(0).getOriginalPositionOrDefault(27));
      assertEquals(24, outlineCallSiteFrames.get(1).getOriginalPositionOrDefault(27));
    }
  }
}
