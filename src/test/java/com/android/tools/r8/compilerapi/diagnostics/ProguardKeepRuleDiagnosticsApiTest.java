// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.diagnostics;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.errors.ProguardKeepRuleDiagnostic;
import com.android.tools.r8.errors.UnusedProguardKeepRuleDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.util.Collections;
import org.junit.Test;

public class ProguardKeepRuleDiagnosticsApiTest extends CompilerApiTestRunner {

  public ProguardKeepRuleDiagnosticsApiTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testR8() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runR8);
  }

  private void runTest(ThrowingConsumer<DiagnosticsHandler, Exception> test) throws Exception {
    TestDiagnosticMessagesImpl diagnostics = new TestDiagnosticMessagesImpl();
    test.accept(diagnostics);
    diagnostics
        .assertOnlyWarnings()
        // Check the diagnostic is an instance of both types.
        .assertWarningsMatch(
            allOf(
                diagnosticType(UnusedProguardKeepRuleDiagnostic.class),
                diagnosticMessage(containsString("does not match anything"))))
        .assertWarningsMatch(
            allOf(
                diagnosticType(ProguardKeepRuleDiagnostic.class),
                diagnosticMessage(containsString("does not match anything"))));
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runR8(DiagnosticsHandler handler) throws Exception {
      R8.run(
          R8Command.builder(
                  new DiagnosticsHandler() {
                    @Override
                    public DiagnosticsLevel modifyDiagnosticsLevel(
                        DiagnosticsLevel level, Diagnostic diagnostic) {
                      if (diagnostic instanceof ProguardKeepRuleDiagnostic
                          || diagnostic instanceof UnusedProguardKeepRuleDiagnostic) {
                        return handler.modifyDiagnosticsLevel(DiagnosticsLevel.WARNING, diagnostic);
                      }
                      return handler.modifyDiagnosticsLevel(level, diagnostic);
                    }

                    @Override
                    public void error(Diagnostic error) {
                      handler.error(error);
                    }

                    @Override
                    public void warning(Diagnostic warning) {
                      handler.warning(warning);
                    }

                    @Override
                    public void info(Diagnostic info) {
                      handler.info(info);
                    }
                  })
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addProguardConfiguration(getKeepMainRules(getMockClass()), Origin.unknown())
              .addProguardConfiguration(
                  Collections.singletonList("-keep class NotPresent {}"), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .setMinApiLevel(1)
              .build());
    }

    @Test
    public void testR8() throws Exception {
      runR8(
          new DiagnosticsHandler() {
            @Override
            public void warning(Diagnostic warning) {
              // ignore the warnings.
            }
          });
    }
  }
}
