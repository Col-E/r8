// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceMethodElement;
import com.android.tools.r8.retrace.Retracer;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiSynthesizedMethodTest extends RetraceApiTestBase {

  public RetraceApiSynthesizedMethodTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final String mapping =
        "# { id: 'com.android.tools.r8.mapping', version: '1.0' }\n"
            + "some.Class -> a:\n"
            + "  void foo() -> a\n"
            + "  # { id: 'com.android.tools.r8.synthesized' }";

    @Test
    public void testSyntheticClass() {
      List<RetraceMethodElement> methodResults =
          Retracer.createDefault(
                  ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {})
              .retraceClass(Reference.classFromTypeName("a"))
              .stream()
              .flatMap(element -> element.lookupMethod("a").stream())
              .collect(Collectors.toList());
      assertEquals(1, methodResults.size());
      RetraceMethodElement retraceMethodElement = methodResults.get(0);
      assertFalse(retraceMethodElement.isUnknown());
      assertTrue(methodResults.get(0).isCompilerSynthesized());
    }
  }
}
