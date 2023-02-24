// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetracedClassReference;
import com.android.tools.r8.retrace.Retracer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiEmptyTest extends RetraceApiTestBase {

  public RetraceApiEmptyTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return RetraceTest.class;
  }

  public static class RetraceTest implements RetraceApiBinaryTest {

    @Test
    public void testNone() {
      String expected = "hello.World";
      List<RetracedClassReference> retracedClasses = new ArrayList<>();
      Retracer.createDefault(ProguardMapProducer.fromString(""), new DiagnosticsHandler() {})
          .retraceClass(Reference.classFromTypeName(expected))
          .stream()
          .forEach(
              result -> {
                retracedClasses.add(result.getRetracedClass());
              });
      assertEquals(1, retracedClasses.size());
      RetracedClassReference retracedClass = retracedClasses.get(0);
      assertEquals(retracedClass.getTypeName(), expected);
    }
  }
}
