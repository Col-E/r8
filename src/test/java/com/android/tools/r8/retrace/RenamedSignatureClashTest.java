// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RenamedSignatureClashTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private final ClassReference renamedHolder = Reference.classFromTypeName("A");
  private final ClassReference originalHolder =
      Reference.classFromTypeName("com.android.tools.r8.naming.retrace.Main");

  private final String mapping =
      StringUtils.lines(
          "# { id: 'com.android.tools.r8.mapping', version: '1.0' }",
          originalHolder.getTypeName() + " -> " + renamedHolder.getTypeName() + ":",
          "  void some.moved.Method.someMethod(int) -> a",
          "  void methodWithRemovedArgument(int) -> a",
          "  # { id: 'com.android.tools.r8.synthesized' }");

  @Test
  public void testAmbiguousResult() {
    Retracer retracer =
        Retracer.createDefault(
            ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {});
    MethodReference methodReference =
        Reference.methodFromDescriptor(renamedHolder.getDescriptor(), "a", "(I)V");
    RetraceMethodResult retraceMethodResult = retracer.retraceMethod(methodReference);
    assertTrue(retraceMethodResult.isAmbiguous());
    retraceMethodResult.stream()
        .forEach(
            result -> {
              String method = result.getRetracedMethod().asKnown().getMethodReference().toString();
              if (method.equals("Lsome/moved/Method;someMethod(I)V")) {
                assertFalse(result.isCompilerSynthesized());
              } else {
                assertEquals(
                    originalHolder.getDescriptor() + "methodWithRemovedArgument(I)V", method);
                assertTrue(result.isCompilerSynthesized());
              }
            });
  }
}
