// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.constantdynamic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.errors.ConstantDynamicDesugarDiagnostic;
import com.android.tools.r8.errors.UnsupportedConstDynamicDiagnostic;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SharedBootstrapMethodConstantDynamicTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("true", "true", "true", "true", "true");
  private static final Class<?> MAIN_CLASS = A.class;

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK11));
    assumeTrue(parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));

    testForJvm(parameters)
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8CfNoDesugar() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK11));
    assumeTrue(parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));

    testForD8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .setNoMinApi()
        .disableDesugaring()
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8Cf() throws Exception {
    parameters.assumeCfRuntime();
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .setDiagnosticsLevelModifier(
            (level, diagnostic) ->
                (diagnostic instanceof ConstantDynamicDesugarDiagnostic
                        || diagnostic instanceof UnsupportedFeatureDiagnostic)
                    ? DiagnosticsLevel.WARNING
                    : level)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertAllWarningsMatch(
                        anyOf(
                            allOf(
                                diagnosticType(UnsupportedConstDynamicDiagnostic.class),
                                diagnosticMessage(containsString("const-dynamic"))),
                            allOf(
                                diagnosticType(ConstantDynamicDesugarDiagnostic.class),
                                diagnosticMessage(
                                    containsString(
                                        "Unsupported dynamic constant (different owner)")))))
                    .assertOnlyWarnings())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .assertFailureWithErrorThatMatches(containsString("const-dynamic"));
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .setDiagnosticsLevelModifier(
            (level, diagnostic) ->
                (diagnostic instanceof ConstantDynamicDesugarDiagnostic
                        || diagnostic instanceof UnsupportedFeatureDiagnostic)
                    ? DiagnosticsLevel.WARNING
                    : level)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertOnlyWarnings();
              diagnostics.assertAllWarningsMatch(
                  anyOf(
                      allOf(
                          diagnosticType(UnsupportedConstDynamicDiagnostic.class),
                          diagnosticMessage(containsString("const-dynamic"))),
                      allOf(
                          diagnosticType(ConstantDynamicDesugarDiagnostic.class),
                          diagnosticMessage(
                              containsString("Unsupported dynamic constant (different owner)")))));
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .assertFailureWithErrorThatMatches(containsString("const-dynamic"));
  }

  @Test
  public void testR8Cf() {
    parameters.assumeCfRuntime().assumeR8TestParameters();
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClassFileData(getTransformedClasses())
                .addKeepMainRule(MAIN_CLASS)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics.assertOnlyErrors();
                      diagnostics.assertErrorsMatch(
                          diagnosticMessage(
                              containsString("Unsupported dynamic constant (not desugaring)")));
                    }));
  }

  @Test
  public void testR8Dex() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedClasses())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_CLASS)
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics
                  .assertAllWarningsMatch(
                      anyOf(
                          allOf(
                              diagnosticType(UnsupportedConstDynamicDiagnostic.class),
                              diagnosticMessage(containsString("const-dynamic"))),
                          allOf(
                              diagnosticType(ConstantDynamicDesugarDiagnostic.class),
                              diagnosticMessage(
                                  containsString(
                                      "Unsupported dynamic constant (different owner)")))))
                  .assertOnlyWarnings();
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .assertFailureWithErrorThatMatches(containsString("const-dynamic"));
  }

  private Collection<byte[]> getTransformedClasses() throws IOException {
    return ImmutableList.of(
        transformer(A.class)
            .setVersion(CfVersion.V11)
            .transformConstStringToConstantDynamic(
                "condy1", A.class, "myConstant", false, "constantName", Object.class)
            .transformConstStringToConstantDynamic(
                "condy2", A.class, "myConstant", false, "constantName", Object.class)
            .transform(),
        transformer(B.class)
            .setVersion(CfVersion.V11)
            .transformConstStringToConstantDynamic(
                "condy3", A.class, "myConstant", false, "constantName", Object.class)
            .transformConstStringToConstantDynamic(
                "condy4", A.class, "myConstant", false, "constantName", Object.class)
            .transform());
  }

  public static class A {

    public static Object f() {
      return "condy1"; // Will be transformed to Constant_DYNAMIC.
    }

    public static Object g() {
      return "condy2"; // Will be transformed to Constant_DYNAMIC.
    }

    public static void main(String[] args) {
      System.out.println(A.f() != null);
      System.out.println(A.f() == A.g());
      System.out.println(B.f() != null);
      System.out.println(B.f() == B.g());
      System.out.println(A.f() != B.g());
    }

    public static Object myConstant(MethodHandles.Lookup lookup, String name, Class<?> type) {
      return new Object();
    }
  }

  public static class B {

    public static Object f() {
      return "condy3"; // Will be transformed to Constant_DYNAMIC.
    }

    public static Object g() {
      return "condy4"; // Will be transformed to Constant_DYNAMIC.
    }
  }
}
