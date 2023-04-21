// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.examples.jdk17.EnumSealed;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// TODO(b/227160052): Enums with subclasses are compiled to sealed classes by javac from JDK-17.
// This means that projects can implicitly start having sealed classes, and As R8 does not support
// sealed classes for class file output such library projects cannot be compiled.
@RunWith(Parameterized.class)
public class SealedEnumTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  static final String EXPECTED = StringUtils.lines("A", "a B");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    testForJvm(parameters)
        .addRunClasspathFiles(EnumSealed.jar())
        .run(parameters.getRuntime(), EnumSealed.Main.typeName())
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .addProgramFiles(EnumSealed.jar())
        .run(parameters.getRuntime(), EnumSealed.Main.typeName())
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
            .addProgramFiles(EnumSealed.jar())
            .setMinApi(parameters)
            .addKeepMainRule(EnumSealed.Main.typeName());
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
          .run(parameters.getRuntime(), EnumSealed.Main.typeName())
          .assertSuccessWithOutput(EXPECTED);
    }
  }
}
