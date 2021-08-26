// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.constantdynamic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.OriginMatcher.hasParent;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BasicConstantDynamicTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("true", "true");

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    assumeTrue(parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11));
    assumeTrue(parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));

    testForJvm()
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), A.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.getRuntime().isDex());

    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(parameters.getBackend())
                .addProgramClassFileData(getTransformedClasses())
                .setMinApi(parameters.getApiLevel())
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics.assertOnlyErrors();
                      diagnostics.assertErrorsMatch(
                          allOf(
                              diagnosticMessage(containsString("Unsupported dynamic constant")),
                              diagnosticOrigin(hasParent(Origin.unknown()))));
                    }));
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue(parameters.isDexRuntime() || parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));

    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClassFileData(getTransformedClasses())
                .setMinApi(parameters.getApiLevel())
                .addKeepMainRule(A.class)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics.assertOnlyErrors();
                      diagnostics.assertErrorsMatch(
                          allOf(
                              diagnosticMessage(containsString("Unsupported dynamic constant")),
                              diagnosticOrigin(hasParent(Origin.unknown()))));
                    }));
  }

  private byte[] getTransformedClasses() throws IOException {
    return transformer(A.class)
        .setVersion(CfVersion.V11)
        .transformConstStringToConstantDynamic(
            "condy1", A.class, "myConstant", "constantName", Object.class)
        .transformConstStringToConstantDynamic(
            "condy2", A.class, "myConstant", "constantName", Object.class)
        .transform();
  }

  public static class A {

    public static Object f() {
      return "condy1"; // Will be transformed to Constant_DYNAMIC.
    }

    public static Object g() {
      return "condy2"; // Will be transformed to Constant_DYNAMIC.
    }

    public static void main(String[] args) {
      System.out.println(f() != null);
      System.out.println(f() == g());
    }

    private static Object myConstant(MethodHandles.Lookup lookup, String name, Class<?> type) {
      return new Object();
    }
  }
}
