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
import com.android.tools.r8.errors.UnsupportedConstDynamicDiagnostic;
import com.android.tools.r8.errors.UnsupportedConstMethodHandleDiagnostic;
import com.android.tools.r8.errors.UnsupportedConstMethodTypeDiagnostic;
import com.android.tools.r8.errors.UnsupportedDefaultInterfaceMethodDiagnostic;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.errors.UnsupportedInvokeCustomDiagnostic;
import com.android.tools.r8.errors.UnsupportedInvokePolymorphicMethodHandleDiagnostic;
import com.android.tools.r8.errors.UnsupportedInvokePolymorphicVarHandleDiagnostic;
import com.android.tools.r8.errors.UnsupportedPrivateInterfaceMethodDiagnostic;
import com.android.tools.r8.errors.UnsupportedStaticInterfaceMethodDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.util.function.BiFunction;
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
  public void test() {
    check(UnsupportedDefaultInterfaceMethodDiagnostic::new, "default-interface-method", 24);
    check(UnsupportedStaticInterfaceMethodDiagnostic::new, "static-interface-method", 24);
    check(UnsupportedPrivateInterfaceMethodDiagnostic::new, "private-interface-method", 24);
    check(UnsupportedInvokeCustomDiagnostic::new, "invoke-custom", 26);
    check(
        UnsupportedInvokePolymorphicMethodHandleDiagnostic::new,
        "invoke-polymorphic-method-handle",
        26);
    check(
        UnsupportedInvokePolymorphicVarHandleDiagnostic::new, "invoke-polymorphic-var-handle", 28);
    check(UnsupportedConstMethodHandleDiagnostic::new, "const-method-handle", 28);
    check(UnsupportedConstMethodTypeDiagnostic::new, "const-method-type", 28);
    check(UnsupportedConstDynamicDiagnostic::new, "const-dynamic", -1);
  }

  public void check(
      BiFunction<Origin, Position, UnsupportedFeatureDiagnostic> makeFn,
      String descriptor,
      int level) {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    test.run(
        makeFn.apply(Origin.unknown(), Position.UNKNOWN),
        result -> assertEquals(descriptor + " @ " + level, result));
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
              if (warning instanceof UnsupportedConstDynamicDiagnostic
                  || warning instanceof UnsupportedConstMethodHandleDiagnostic
                  || warning instanceof UnsupportedConstMethodTypeDiagnostic
                  || warning instanceof UnsupportedDefaultInterfaceMethodDiagnostic
                  || warning instanceof UnsupportedInvokeCustomDiagnostic
                  || warning instanceof UnsupportedInvokePolymorphicMethodHandleDiagnostic
                  || warning instanceof UnsupportedInvokePolymorphicVarHandleDiagnostic
                  || warning instanceof UnsupportedPrivateInterfaceMethodDiagnostic
                  || warning instanceof UnsupportedStaticInterfaceMethodDiagnostic
                  || warning instanceof UnsupportedFeatureDiagnostic) {
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
    public void test() {
      run(null, str -> {});
    }
  }
}
