// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
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
public class RetraceApiOutlineInOutlineStackTrace extends RetraceApiTestBase {

  public RetraceApiOutlineInOutlineStackTrace(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final ClassReference outline1Renamed = Reference.classFromTypeName("a");
    private final ClassReference outline2Renamed = Reference.classFromTypeName("b");
    private final ClassReference callsiteOriginal = Reference.classFromTypeName("some.Class");
    private final ClassReference callsiteRenamed = Reference.classFromTypeName("c");

    private final String mapping =
        "# { id: 'com.android.tools.r8.mapping', version: '2.0' }\n"
            + "outline1.Class -> "
            + outline1Renamed.getTypeName()
            + ":\n"
            + "  3:4:int outline():0:0 -> a\n"
            + "# { 'id':'com.android.tools.r8.outline' }\n"
            + "outline2.Class -> "
            + outline2Renamed.getTypeName()
            + ":\n"
            + "  6:6:int outline():0:0 -> a\n"
            + "# { 'id':'com.android.tools.r8.outlineCallsite', "
            + "     'positions': { '3': 42, '4': 43 } }\n"
            + "  42:43:int outline():0:0 -> a\n" // This is another call to the outline
            + "# { 'id':'com.android.tools.r8.outline' }\n"
            + callsiteOriginal.getTypeName()
            + " -> "
            + callsiteRenamed.getTypeName()
            + ":\n"
            + "  1:1:void foo.bar.Baz.qux():42:42 -> s\n"
            + "  10:11:int foo.bar.baz.outlineCaller(int):98:99 -> s\n"
            + "  28:28:int outlineCaller(int):0:0 -> s\n" // This is the actual call to the outline
            + "# { 'id':'com.android.tools.r8.outlineCallsite', "
            + "     'positions': { '42': 10, '43': 11 } }\n";

    @Test
    public void test() {
      ProguardMappingSupplier mappingSupplier =
          ProguardMappingSupplier.builder()
              .setProguardMapProducer(ProguardMapProducer.fromString(mapping))
              .build();
      Retracer retracer = mappingSupplier.createRetracer(new DiagnosticsHandler() {});
      // Retrace the first outline.
      RetraceStackTraceContext outlineContext =
          retraceOutline(
              retracer,
              Reference.methodFromDescriptor(outline1Renamed, "a", "()I"),
              4,
              RetraceStackTraceContext.empty());

      // Retrace the second outline using the context of the first retracing.
      outlineContext =
          retraceOutline(
              retracer,
              Reference.methodFromDescriptor(outline2Renamed, "a", "()I"),
              6,
              outlineContext);

      List<RetraceFrameElement> retraceOutlineCallee =
          retracer
              .retraceFrame(
                  outlineContext,
                  OptionalInt.of(28),
                  Reference.methodFromDescriptor(callsiteRenamed, "s", "(I)I"))
              .stream()
              .collect(Collectors.toList());
      assertEquals(1, retraceOutlineCallee.size());

      List<RetracedMethodReference> outlineCallSiteFrames =
          retraceOutlineCallee.get(0).stream()
              .map(RetracedSingleFrame::getMethodReference)
              .collect(Collectors.toList());
      assertEquals(1, outlineCallSiteFrames.size());
      assertEquals(99, outlineCallSiteFrames.get(0).getOriginalPositionOrDefault(28));
    }

    private RetraceStackTraceContext retraceOutline(
        Retracer retracer,
        MethodReference reference,
        int position,
        RetraceStackTraceContext context) {
      List<RetraceFrameElement> outlineRetraced =
          retracer.retraceFrame(context, OptionalInt.of(position), reference).stream()
              .collect(Collectors.toList());
      // The retrace result should not be ambiguous or empty.
      assertEquals(1, outlineRetraced.size());
      RetraceFrameElement retraceFrameElement = outlineRetraced.get(0);

      // Check that visiting all frames report the outline.
      List<RetracedMethodReference> allMethodReferences =
          retraceFrameElement.stream()
              .map(RetracedSingleFrame::getMethodReference)
              .collect(Collectors.toList());
      assertEquals(1, allMethodReferences.size());
      assertEquals(0, allMethodReferences.get(0).getOriginalPositionOrDefault(position));

      // Check that visiting rewritten frames will not report anything.
      List<RetracedMethodReference> rewrittenReferences =
          retraceFrameElement
              .streamRewritten(RetraceStackTraceContext.empty())
              .map(RetracedSingleFrame::getMethodReference)
              .collect(Collectors.toList());
      assertEquals(0, rewrittenReferences.size());

      return retraceFrameElement.getRetraceStackTraceContext();
    }
  }
}
