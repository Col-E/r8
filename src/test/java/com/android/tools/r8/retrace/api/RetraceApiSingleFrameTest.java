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
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.Retracer;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiSingleFrameTest extends RetraceApiTestBase {

  public RetraceApiSingleFrameTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final ClassReference originalClass = Reference.classFromTypeName("some.Class");
    private final ClassReference renamedClass = Reference.classFromTypeName("a");

    private final String mapping =
        originalClass.getTypeName()
            + " -> "
            + renamedClass.getTypeName()
            + ":\n"
            + "  int strawberry(int):99:99 -> a\n";

    @Test
    public void testRetracingFrameEqualToNarrow() {
      Retracer retracer =
          Retracer.createDefault(
              ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {});
      checkResults(
          retracer
              .retraceFrame(
                  RetraceStackTraceContext.empty(),
                  OptionalInt.empty(),
                  Reference.methodFromDescriptor(renamedClass, "a", "(I)I"))
              .stream()
              .collect(Collectors.toList()));
      checkResults(
          retracer
              .retraceClass(renamedClass)
              .lookupFrame(RetraceStackTraceContext.empty(), OptionalInt.empty(), "a")
              .stream()
              .collect(Collectors.toList()));
    }

    private void checkResults(List<RetraceFrameElement> elements) {
      assertEquals(1, elements.size());
      RetracedMethodReference topFrame = elements.get(0).getTopFrame();
      assertEquals("strawberry", topFrame.getMethodName());
      assertEquals(99, topFrame.getOriginalPositionOrDefault(-1));
    }
  }
}
