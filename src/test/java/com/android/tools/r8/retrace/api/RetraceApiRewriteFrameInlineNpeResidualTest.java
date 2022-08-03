// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceThrownExceptionElement;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.Retracer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiRewriteFrameInlineNpeResidualTest extends RetraceApiTestBase {

  public RetraceApiRewriteFrameInlineNpeResidualTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final ClassReference originalException = Reference.classFromTypeName("foo.bar.baz");
    private final ClassReference renamedException = Reference.classFromTypeName("c");

    private final ClassReference originalSomeClass = Reference.classFromTypeName("some.Class");
    private final ClassReference renamedSomeClass = Reference.classFromTypeName("a");

    private final ClassReference originalSomeOtherClass =
        Reference.classFromTypeName("some.other.Class");
    private final ClassReference renamedSomeOtherClass = Reference.classFromTypeName("b");

    private final String mapping =
        "# { id: 'com.android.tools.r8.mapping', version: '2.0' }\n"
            + originalException.getTypeName()
            + " -> "
            + renamedException.getTypeName()
            + ":\n"
            + originalSomeClass.getTypeName()
            + " -> "
            + renamedSomeClass.getTypeName()
            + ":\n"
            + "  4:4:void other.Class.inlinee():23:23 -> a\n"
            + "  4:4:void caller(other.Class):7 -> a\n"
            + "  # { id: 'com.android.tools.r8.rewriteFrame', "
            + "      conditions: ['throws(Lc;)'], "
            + "      actions: ['removeInnerFrames(1)'] "
            + "    }\n"
            + originalSomeOtherClass.getTypeName()
            + " -> "
            + renamedSomeOtherClass.getTypeName()
            + ":\n"
            + "  4:4:void other.Class.inlinee2():23:23 -> a\n"
            + "  4:4:void caller(other.Class):7 -> a\n"
            + "  # { id: 'com.android.tools.r8.rewriteFrame', "
            + "      conditions: ['throws(Lfoo/bar/baz;)'], "
            + "      actions: ['removeInnerFrames(1)'] "
            + "    }";

    @Test
    public void testUsingObfuscatedName() {
      ProguardMappingSupplier mappingSupplier =
          ProguardMappingSupplier.builder()
              .setProguardMapProducer(ProguardMapProducer.fromString(mapping))
              .build();
      Retracer retracer = mappingSupplier.createRetracer(new DiagnosticsHandler() {});
      List<RetraceThrownExceptionElement> npeRetraced =
          retracer.retraceThrownException(renamedException).stream().collect(Collectors.toList());
      assertEquals(1, npeRetraced.size());
      assertEquals(originalException, npeRetraced.get(0).getRetracedClass().getClassReference());

      checkRewrittenFrame(
          retracer, npeRetraced.get(0).getContext(), renamedSomeClass.getTypeName(), "a", true);
      checkRewrittenFrame(
          retracer,
          npeRetraced.get(0).getContext(),
          renamedSomeOtherClass.getTypeName(),
          "a",
          false);
    }

    private void checkRewrittenFrame(
        Retracer retracer,
        RetraceStackTraceContext throwingContext,
        String typeName,
        String methodName,
        boolean shouldRemove) {
      List<RetraceFrameElement> retraceFrameElements =
          retracer.retraceClass(Reference.classFromTypeName(typeName)).stream()
              .flatMap(
                  element ->
                      element.lookupFrame(throwingContext, OptionalInt.of(4), methodName).stream())
              .collect(Collectors.toList());
      assertEquals(1, retraceFrameElements.size());

      RetraceFrameElement retraceFrameElement = retraceFrameElements.get(0);
      // Check that rewriting the frames will remove the top 1 frames if the condition is active.
      Map<Integer, RetracedMethodReference> results = new LinkedHashMap<>();
      retraceFrameElement.forEachRewritten(
          frame -> {
            RetracedMethodReference existingValue =
                results.put(frame.getIndex(), frame.getMethodReference());
            assertNull(existingValue);
          });
      if (shouldRemove) {
        assertEquals(1, results.size());
        assertEquals(7, results.get(0).getOriginalPositionOrDefault(4));
        assertEquals(results.get(0).getMethodName(), "caller");
      } else {
        assertEquals(2, results.size());
      }
    }
  }
}
