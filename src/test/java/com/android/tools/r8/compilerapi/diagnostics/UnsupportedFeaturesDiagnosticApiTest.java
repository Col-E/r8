// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.diagnostics;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.errors.InvokeCustomDiagnostic;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.util.function.Consumer;
import org.junit.Test;

public class UnsupportedFeaturesDiagnosticApiTest extends CompilerApiTestRunner {

  public UnsupportedFeaturesDiagnosticApiTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void test() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    test.run(
        new InvokeCustomDiagnostic(Origin.unknown(), Position.UNKNOWN),
        result -> {
          assertEquals("invoke-custom @ 26", result);
        });
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run(Diagnostic diagnostic, Consumer<String> consumer) {
      DiagnosticsHandler myHandler =
          new DiagnosticsHandler() {
            @Override
            public void warning(Diagnostic warning) {
              if (warning instanceof UnsupportedFeatureDiagnostic) {
                UnsupportedFeatureDiagnostic unsupportedFeature =
                    (UnsupportedFeatureDiagnostic) warning;
                String featureDescriptor = unsupportedFeature.getFeatureDescriptor();
                int supportedApiLevel = unsupportedFeature.getSupportedApiLevel();
                consumer.accept(featureDescriptor + " @ " + supportedApiLevel);
              }
            }
          };
      myHandler.warning(diagnostic);
    }

    @Test
    public void test() throws Exception {
      run(null, str -> {});
    }
  }
}
