// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetraceUnknownMappingInformationElement;
import com.android.tools.r8.retrace.Retracer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RetraceApiUnknownJsonTest extends RetraceApiTestBase {

  public RetraceApiUnknownJsonTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  protected Class<? extends RetraceApiBinaryTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class ApiTest implements RetraceApiBinaryTest {

    private final String extraMapping =
        "{\"id\":\"some.information.with.context\",\"value\":\"Hello World\"}";
    private final String mapping =
        "# { 'id': 'com.android.tools.r8.mapping', version: '1.0' }\n"
            + "# { 'id': 'some.information.without.context' }\n"
            + "some.Class -> a:\n"
            + "# "
            + extraMapping
            + "\n"
            + "# {'id': 'sourceFile','fileName':'SomeFileName.kt'}\n"
            + "  1:3:int strawberry(int):99:101 -> s\n"
            + "  4:5:int mango(float):121:122 -> s\n";

    @Test
    public void testRetracingSourceFile() {
      TestDiagnosticsHandler diagnosticsHandler = new TestDiagnosticsHandler();
      List<RetraceUnknownMappingInformationElement> mappingInfos =
          Retracer.createDefault(ProguardMapProducer.fromString(mapping), diagnosticsHandler)
              .retraceClass(Reference.classFromTypeName("a"))
              .stream()
              .flatMap(element -> element.getUnknownJsonMappingInformation().stream())
              .collect(Collectors.toList());
      assertEquals(1, mappingInfos.size());
      RetraceUnknownMappingInformationElement unknownMapping = mappingInfos.get(0);
      assertEquals("some.information.with.context", unknownMapping.getIdentifier());
      assertEquals(extraMapping, unknownMapping.getPayLoad());

      assertEquals(2, diagnosticsHandler.infoMessages.size());
      assertEquals(
          "Could not find a handler for some.information.without.context",
          diagnosticsHandler.infoMessages.get(0).getDiagnosticMessage());
      assertEquals(
          "Could not find a handler for some.information.with.context",
          diagnosticsHandler.infoMessages.get(1).getDiagnosticMessage());
    }

    private static class TestDiagnosticsHandler implements com.android.tools.r8.DiagnosticsHandler {

      private List<Diagnostic> infoMessages = new ArrayList<>();

      @Override
      public void warning(Diagnostic warning) {
        throw new RuntimeException("Warning not expected");
      }

      @Override
      public void error(Diagnostic error) {
        throw new RuntimeException("Error not expected");
      }

      @Override
      public void info(Diagnostic info) {
        DiagnosticsHandler.super.info(info);
        infoMessages.add(info);
      }
    }
  }
}
