// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.Retracer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiAmbiguousOriginalRangeTest extends RetraceApiTestBase {

  public RetraceApiAmbiguousOriginalRangeTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final ClassReference renamedHolder = Reference.classFromTypeName("a");

    private final String mapping =
        "com.android.tools.r8.naming.retrace.Main -> a:\n"
            + "  1:1:void method():42:44 -> a\n"
            + "  2:4:void method():45:46 -> a\n"
            + "  5:6:void method():47:47 -> a\n";

    @Test
    public void testAmbiguousResult() {
      Retracer retracer =
          Retracer.createDefault(
              ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {});
      MethodReference methodReference =
          Reference.methodFromDescriptor(renamedHolder.getDescriptor(), "a", "()V");

      // Check that retracing with position one is ambiguous between line 42, 43 and 44.
      RetraceFrameResult retraceFrameResult =
          retracer.retraceFrame(methodReference, OptionalInt.of(1));
      assertTrue(retraceFrameResult.isAmbiguous());
      List<Integer> originalPositions =
          retraceFrameResult.stream()
              .map(element -> element.getTopFrame().getOriginalPositionOrDefault(1))
              .collect(Collectors.toList());
      assertEquals(Arrays.asList(42, 43, 44), originalPositions);

      // Check that retracing with position 3 is ambiguous between 45 and 46.
      retraceFrameResult = retracer.retraceFrame(methodReference, OptionalInt.of(3));
      assertTrue(retraceFrameResult.isAmbiguous());
      originalPositions =
          retraceFrameResult.stream()
              .map(element -> element.getTopFrame().getOriginalPositionOrDefault(3))
              .collect(Collectors.toList());
      assertEquals(Arrays.asList(45, 46), originalPositions);

      // Check that retracing with position 5 is not ambiguous.
      retraceFrameResult = retracer.retraceFrame(methodReference, OptionalInt.of(5));
      assertFalse(retraceFrameResult.isAmbiguous());
      originalPositions =
          retraceFrameResult.stream()
              .map(element -> element.getTopFrame().getOriginalPositionOrDefault(5))
              .collect(Collectors.toList());
      assertEquals(Collections.singletonList(47), originalPositions);
    }
  }
}
