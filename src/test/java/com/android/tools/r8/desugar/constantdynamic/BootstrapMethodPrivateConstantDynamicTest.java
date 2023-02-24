// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.constantdynamic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.CfVersion;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BootstrapMethodPrivateConstantDynamicTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final Class<?> MAIN_CLASS = A.class;

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11));
    testForJvm(parameters)
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .applyIf(
            // When not desugaring the CF code requires JDK 11.
            DesugarTestConfiguration::isNotDesugared,
            r -> {
              if (parameters.isCfRuntime()
                  && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11)) {
                r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
              } else {
                r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class);
              }
            },
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepMainRule(A.class)
        // TODO(b/198142625): Support CONSTANT_Dynamic output for class files.
        .applyIf(
            parameters.isCfRuntime(),
            r -> {
              assertThrows(
                  CompilationFailedException.class,
                  () ->
                      r.compileWithExpectedDiagnostics(
                          diagnostics -> {
                            diagnostics.assertOnlyErrors();
                            diagnostics.assertErrorsMatch(
                                diagnosticMessage(
                                    containsString(
                                        "Unsupported dynamic constant (not desugaring)")));
                          }));
            },
            r ->
                r.run(parameters.getRuntime(), MAIN_CLASS)
                    .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class));
  }

  private byte[] getTransformedClasses() throws IOException {
    return transformer(A.class)
        .setVersion(CfVersion.V11)
        .transformConstStringToConstantDynamic(
            "condy1", A.class, "myConstant", false, "constantName", Object.class)
        .transform();
  }

  public static class A {

    public static Object f() {
      return "condy1"; // Will be transformed to Constant_DYNAMIC.
    }

    public static void main(String[] args) {
      System.out.println(f() != null);
    }

    private Object myConstant(MethodHandles.Lookup lookup, String name, Class<?> type) {
      return new Object();
    }
  }
}
