// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.mappinginformation.MappingInformationDiagnostics;
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
          + "  1:2:int method2():3:4 -> a\n"
          + "  # { id:'com.android.tools.r8.residualsignature',signature:'Z' }\n"
          + "  int method3() -> b\n"
          + "  # { id:'com.android.tools.r8.residualsignature',signature:'VOLAPYK' }\n";

  @Test
  public void testInvalidResidualMapping() {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    Retracer.createDefault(ProguardMapProducer.fromString(mapping), diagnosticsHandler)
        .retraceMethod(
            Reference.method(
                someClassRenamed,
                "method1",
                Collections.emptyList(),
                Reference.primitiveFromDescriptor("I")));
    diagnosticsHandler.assertOnlyWarnings();
    diagnosticsHandler.assertAllWarningsMatch(
        DiagnosticsMatcher.diagnosticType(MappingInformationDiagnostics.class));
    diagnosticsHandler.assertWarningsMatch(
        DiagnosticsMatcher.diagnosticMessage(containsString(getMessage("java.lang.Object a()"))),
        DiagnosticsMatcher.diagnosticMessage(
            containsString(
                getMessage(
                    "{\"id\":\"com.android.tools.r8.residualsignature\",\"signature\":\"I\"}"))),
        DiagnosticsMatcher.diagnosticMessage(
            containsString(
                getMessage(
                    "{\"id\":\"com.android.tools.r8.residualsignature\",\"signature\":\"Z\"}"))),
        DiagnosticsMatcher.diagnosticMessage(
            containsString(
                "The residual signature mapping '# {"
                    + " id:'com.android.tools.r8.residualsignature',signature:'VOLAPYK' }' is"
                    + " invalid")));
  }

  private String getMessage(String variable) {
    return "The residual signature mapping '"
        + variable
        + "' is not of the same type as the member it describes.";
  }
}
