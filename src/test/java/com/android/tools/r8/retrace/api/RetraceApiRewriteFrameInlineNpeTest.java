// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceThrownExceptionElement;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.Retracer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiRewriteFrameInlineNpeTest extends RetraceApiTestBase {

  public RetraceApiRewriteFrameInlineNpeTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final String npeDescriptor = "Ljava/lang/NullPointerException;";

    private final String mapping =
        "# { id: 'com.android.tools.r8.mapping', version: '2.0' }\n"
            + "some.Class -> a:\n"
            + "  4:4:void other.Class.inlinee():23:23 -> a\n"
            + "  4:4:void caller(other.Class):7 -> a\n"
            + "  # { id: 'com.android.tools.r8.rewriteFrame', "
            + "      conditions: ['throws("
            + npeDescriptor
            + ")'], "
            + "      actions: ['removeInnerFrames(1)']"
            + "    }";

    @Test
    public void testFirstStackLineIsRemoved() {
      TestDiagnosticsHandler testDiagnosticsHandler = new TestDiagnosticsHandler();
      ProguardMappingSupplier mappingSupplier =
          ProguardMappingSupplier.builder()
              .setProguardMapProducer(ProguardMapProducer.fromString(mapping))
              .build();
      Retracer retracer = mappingSupplier.createRetracer(testDiagnosticsHandler);

      List<RetraceThrownExceptionElement> npeRetraced =
          retracer.retraceThrownException(Reference.classFromDescriptor(npeDescriptor)).stream()
              .collect(Collectors.toList());
      assertEquals(1, npeRetraced.size());

      RetraceStackTraceContext throwingContext = npeRetraced.get(0).getContext();

      List<RetraceFrameElement> retraceFrameElements =
          retracer.retraceClass(Reference.classFromTypeName("a")).stream()
              .flatMap(
                  element -> element.lookupFrame(throwingContext, OptionalInt.of(4), "a").stream())
              .collect(Collectors.toList());
      assertEquals(1, retraceFrameElements.size());

      RetraceFrameElement retraceFrameElement = retraceFrameElements.get(0);
      // Check that rewriting the frames will remove the top 1 frames if the condition is active.
      Map<Integer, RetracedMethodReference> results = new LinkedHashMap<>();
      retraceFrameElement
          .streamRewritten(throwingContext)
          .forEach(
              frame -> {
                RetracedMethodReference existingValue =
                    results.put(frame.getIndex(), frame.getMethodReference());
                assertNull(existingValue);
              });
      assertEquals(1, results.size());
      assertEquals(7, results.get(0).getOriginalPositionOrDefault(4));
      assertEquals(results.get(0).getMethodName(), "caller");
    }

    private static class TestDiagnosticsHandler implements com.android.tools.r8.DiagnosticsHandler {

      private List<Diagnostic> infoMessages = new ArrayList<>();

      @Override
      public void warning(Diagnostic warning) {
        throw new RuntimeException("Warning not expected");
      }

      @Override
      public void error(Diagnostic error) {
        throw new RuntimeException("Error not expected");
      }

      @Override
      public void info(Diagnostic info) {
        DiagnosticsHandler.super.info(info);
        infoMessages.add(info);
      }
    }
  }
}
