// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetracedSourceFile;
import com.android.tools.r8.retrace.Retracer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiInferSourceFileTest extends RetraceApiTestBase {

  public RetraceApiInferSourceFileTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private static final String mapping =
        "some.Class -> a:\n"
            + "  1:3:int strawberry(int):99:101 -> s\n"
            + "  4:5:int mango(float):121:122 -> s";

    @Test
    public void testRetracingSourceFile() {
      List<RetracedSourceFile> sourceFileResults = new ArrayList<>();
      Retracer.createDefault(ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {})
          .retraceClass(Reference.classFromTypeName("a"))
          .forEach(clazz -> sourceFileResults.add(clazz.getSourceFile()));
      assertEquals(1, sourceFileResults.size());
      assertFalse(sourceFileResults.get(0).hasRetraceResult());
    }
  }
}
