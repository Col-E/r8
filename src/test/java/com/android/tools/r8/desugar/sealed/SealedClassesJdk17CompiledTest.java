// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.examples.jdk17.Sealed;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesJdk17CompiledTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  static final String EXPECTED = StringUtils.lines("R8 compiler", "D8 compiler");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    testForJvm(parameters)
        .addRunClasspathFiles(Sealed.jar())
        .run(parameters.getRuntime(), Sealed.Main.typeName())
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addProgramFiles(Sealed.jar())
        .run(parameters.getRuntime(), Sealed.Main.typeName())
        .applyIf(
            c ->
                DesugarTestConfiguration.isNotJavac(c)
                    || parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addProgramFiles(Sealed.jar())
            .setMinApi(parameters)
            // Keep the sealed class to ensure the PermittedSubclasses attribute stays live.
            .addKeepPermittedSubclasses(Sealed.Compiler.typeName())
            .addKeepMainRule(Sealed.Main.typeName());
    if (parameters.isCfRuntime()) {
      assertThrows(
          CompilationFailedException.class,
          () ->
              builder.compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics.assertErrorThatMatches(
                          diagnosticMessage(
                              containsString(
                                  "Sealed classes are not supported as program classes")))));
    } else {
      builder
          .run(parameters.getRuntime(), Sealed.Main.typeName())
          .assertSuccessWithOutput(EXPECTED);
    }
  }
}
