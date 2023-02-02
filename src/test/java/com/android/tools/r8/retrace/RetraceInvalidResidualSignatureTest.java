// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/267413327 */
@RunWith(Parameterized.class)
public class RetraceInvalidResidualSignatureTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetraceInvalidResidualSignatureTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private final ClassReference someClassOriginal = Reference.classFromTypeName("some.Class");
  private final ClassReference someClassRenamed = Reference.classFromTypeName("b");

  private final String mapping =
      "# { id: 'com.android.tools.r8.mapping', version: '2.2' }\n"
          + someClassOriginal.getTypeName()
          + " -> "
          + someClassRenamed.getTypeName()
          + ":\n"
          + "  int f -> a\n"
          + "  # { id:'com.android.tools.r8.residualsignature',signature:'()Ljava/lang/Object;' }\n"
          + "  int method1() -> a\n"
          + "  # { id:'com.android.tools.r8.residualsignature',signature:'I' }\n"
          + "  int method2() -> b\n"
          + "  # { id:'com.android.tools.r8.residualsignature',signature:'VOLAPYK' }\n";

  @Test
  public void testInvalidResidualMapping() {
    // TODO(b/267413327): Fail more gracefully than just throwing.
    assertThrows(
        AssertionError.class,
        () ->
            Retracer.createDefault(
                    ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {})
                .retraceMethod(
                    Reference.method(
                        someClassRenamed,
                        "method1",
                        Collections.emptyList(),
                        Reference.primitiveFromDescriptor("I"))));
  }
}
