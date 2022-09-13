// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.retrace.RetraceFieldElement;
import com.android.tools.r8.retrace.RetraceMethodElement;
import com.android.tools.r8.retrace.Retracer;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiResidualSignatureTest extends RetraceApiTestBase {

  public RetraceApiResidualSignatureTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private static final String mapping =
        "# { id: 'com.android.tools.r8.mapping', version: 'experimental' }\n"
            + "some.Class -> a:\n"
            + "  some.SuperType field -> a\n"
            + "  # { id: 'com.android.tools.r8.residualsignature', signature: 'Ljava/lang/Object;'"
            + " }\n"
            + "  some.SubType field -> a\n"
            + "  # { id: 'com.android.tools.r8.residualsignature', signature: 'Lsome/SuperType;'"
            + " }\n"
            + "  void foo(int, int) -> x\n"
            + "  # { id: 'com.android.tools.r8.residualsignature', signature: '(I)V' }\n"
            + "  void foo(int) -> x\n"
            + "  # { id: 'com.android.tools.r8.residualsignature', signature: '()V' }\n";

    @Test
    public void testResidualSignature() {
      Retracer retracer =
          ProguardMappingSupplier.builder()
              .setProguardMapProducer(ProguardMapProducer.fromString(mapping))
              .setAllowExperimental(true)
              .build()
              .createRetracer(new DiagnosticsHandler() {});
      ClassReference aClass = Reference.classFromTypeName("a");
      List<RetraceMethodElement> fooWithTwoArgs =
          retracer.retraceMethod(Reference.methodFromDescriptor(aClass, "x", "(I)V")).stream()
              .collect(Collectors.toList());
      assertEquals(1, fooWithTwoArgs.size());
      assertEquals(
          "Lsome/Class;foo(II)V",
          fooWithTwoArgs.get(0).getRetracedMethod().asKnown().getMethodReference().toString());
      List<RetraceMethodElement> fooWithOneArg =
          retracer.retraceMethod(Reference.methodFromDescriptor(aClass, "x", "()V")).stream()
              .collect(Collectors.toList());
      assertEquals(1, fooWithOneArg.size());
      assertEquals(
          "Lsome/Class;foo(I)V",
          fooWithOneArg.get(0).getRetracedMethod().asKnown().getMethodReference().toString());
      List<RetraceFieldElement> fieldWithSuperType =
          retracer
              .retraceField(
                  Reference.field(aClass, "a", Reference.typeFromTypeName("java.lang.Object")))
              .stream()
              .collect(Collectors.toList());
      // TODO(b/169953605): Use the residual signature information to prune the result.
      assertEquals(2, fieldWithSuperType.size());
      List<RetraceFieldElement> fieldWithSubType =
          retracer
              .retraceField(
                  Reference.field(aClass, "a", Reference.typeFromTypeName("some.SuperType")))
              .stream()
              .collect(Collectors.toList());
      // TODO(b/169953605): Use the residual signature information to prune the result.
      assertEquals(2, fieldWithSubType.size());
    }
  }
}
