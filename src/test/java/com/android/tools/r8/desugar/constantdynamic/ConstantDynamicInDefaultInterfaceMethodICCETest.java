// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
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
import java.lang.invoke.MethodHandles;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstantDynamicInDefaultInterfaceMethodICCETest extends TestBase {

  @Parameter() public TestParameters parameters;

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
        .addProgramClasses(MAIN_CLASS)
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addProgramClasses(MAIN_CLASS)
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
            })
        .applyIf(
            DesugarTestConfiguration::isDesugared,
            r -> r.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClasses(MAIN_CLASS)
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_CLASS)
        // TODO(b/198142625): Support CONSTANT_Dynamic output for class files.
        .applyIf(
            parameters.isCfRuntime(),
            b -> {
              assertThrows(
                  CompilationFailedException.class,
                  () ->
                      b.compileWithExpectedDiagnostics(
                          diagnostics -> {
                            diagnostics.assertOnlyErrors();
                            diagnostics.assertErrorsMatch(
                                diagnosticMessage(
                                    containsString(
                                        "Unsupported dynamic constant (not desugaring)")));
                          }));
            },
            b ->
                b.run(parameters.getRuntime(), MAIN_CLASS)
                    .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class));
  }

  // Create a constant dynamic without the interface bit targeting an interface bootstrap method.
  private byte[] getTransformedClasses() throws Exception {
    return transformer(I.class)
        .setVersion(CfVersion.V11)
        .transformConstStringToConstantDynamic(
            "condy1", I.class, "myConstant", false, "constantName", Object.class)
        .transformConstStringToConstantDynamic(
            "condy2", I.class, "myConstant", false, "constantName", Object.class)
        .setPrivate(
            I.class.getDeclaredMethod(
                "myConstant", MethodHandles.Lookup.class, String.class, Class.class))
        .transform();
  }

  public interface I {

    default Object f() {
      return "condy1"; // Will be transformed to Constant_DYNAMIC.
    }

    default Object g() {
      return "condy2"; // Will be transformed to Constant_DYNAMIC.
    }

    /* private */ static Object myConstant(
        MethodHandles.Lookup lookup, String name, Class<?> type) {
      return new Object();
    }
  }

  public static class A implements I {
    public static void main(String[] args) {
      A a = new A();
      System.out.println(a.f() != null);
      System.out.println(a.f() == a.g());
    }
  }
}
