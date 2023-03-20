// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstantDynamicRegress272346803Test extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private static final Class<?> MAIN_CLASS = Main.class;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("A", "B");
  private static final String UNEXPECTED_OUTPUT = StringUtils.lines("A", "A");

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11));
    testForJvm(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .applyIf(
            // When not desugaring the CF code requires JDK 11.
            DesugarTestConfiguration::isNotDesugared,
            r -> {
              if (parameters.isCfRuntime()
                  && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11)) {
                r.assertSuccessWithOutput(EXPECTED_OUTPUT);
              } else {
                r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class);
              }
            })
        .applyIf(
            DesugarTestConfiguration::isDesugared, r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_CLASS)
        // Access modification is required for inlining the get method from the desugared constant
        // dynamic class.
        .allowAccessModification()
        // TODO(b/198142613): There should not be a warnings on class references which are
        //  desugared away.
        .applyIf(
            parameters.getApiLevel().isLessThan(AndroidApiLevel.O),
            b -> b.addDontWarn("java.lang.invoke.MethodHandles$Lookup"))
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
                    .assertSuccessWithOutput(UNEXPECTED_OUTPUT));
  }

  private List<byte[]> getTransformedClasses() throws IOException {
    return Arrays.asList(
        transformer(A.class)
            .setVersion(CfVersion.V11)
            .transformConstStringToConstantDynamic(
                "condy1", A.class, "myConstant", false, "constantName", String.class)
            .transform(),
        transformer(B.class)
            .setVersion(CfVersion.V11)
            .transformConstStringToConstantDynamic(
                "condy1", B.class, "myConstant", false, "constantName", String.class)
            .transform());
  }

  // When R8 optimize this code the getter for the two constant dynamics will be inlined into
  // Main.main. This leaves the synthetic constant dynamic classes with just two static fields.
  // The synthetic sharing then share these two synthetics leaving only one constant.
  // See b/272346803 for details.
  public static class Main {

    public static void main(String[] args) {
      System.out.println(A.getConstant());
      System.out.println(B.getConstant());
    }
  }

  public static class A {

    public static String getConstant() {
      return "condy1"; // Will be transformed to Constant_DYNAMIC.
    }

    private static Object myConstant(MethodHandles.Lookup lookup, String name, Class<?> type) {
      return "A";
    }
  }

  public static class B {

    public static String getConstant() {
      return "condy1"; // Will be transformed to Constant_DYNAMIC.
    }

    private static Object myConstant(MethodHandles.Lookup lookup, String name, Class<?> type) {
      return "B";
    }
  }
}
