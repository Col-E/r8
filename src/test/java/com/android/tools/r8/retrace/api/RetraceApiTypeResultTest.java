// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceTypeElement;
import com.android.tools.r8.retrace.Retracer;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiTypeResultTest extends RetraceApiTestBase {

  public RetraceApiTypeResultTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final TypeReference minifiedName = Reference.typeFromTypeName("a[]");
    private final TypeReference original = Reference.typeFromTypeName("some.Class[]");

    private static final String mapping =
        "# { id: 'com.android.tools.r8.mapping', version: '1.0' }\nsome.Class -> a:\n";

    @Test
    public void testRetraceClassArray() {
      List<RetraceTypeElement> collect =
          Retracer.createDefault(
                  ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {})
              .retraceType(minifiedName)
              .stream()
              .collect(Collectors.toList());
      assertEquals(1, collect.size());
      assertEquals(original, collect.get(0).getType().getTypeReference());
    }

    @Test
    public void testRetracePrimitiveArray() {
      TypeReference intArr = Reference.typeFromTypeName("int[][]");
      List<RetraceTypeElement> collect =
          Retracer.createDefault(
                  ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {})
              .retraceType(intArr)
              .stream()
              .collect(Collectors.toList());
      assertEquals(1, collect.size());
      assertEquals(intArr, collect.get(0).getType().getTypeReference());
    }
  }
}
