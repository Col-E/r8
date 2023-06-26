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
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  static final String EXPECTED = StringUtils.lines("Success!");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private void addTestClasses(TestBuilder<?, ?> builder) throws Exception {
    builder
        .addProgramClasses(TestClass.class, Sub1.class, Sub2.class)
        .addProgramClassFileData(getTransformedClasses());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    testForJvm(parameters)
        .apply(this::addTestClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Exception {
    testForDesugaring(parameters)
        .apply(this::addTestClasses)
        .run(parameters.getRuntime(), TestClass.class)
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
            .apply(this::addTestClasses)
            .setMinApi(parameters)
            // Keep the sealed class to ensure the PermittedSubclasses attribute stays live.
            .addKeepPermittedSubclasses(C.class)
            .addKeepMainRule(TestClass.class);
    if (parameters.isCfRuntime()) {
      // TODO(b/227160052): Support sealed classes for R8 class file output.
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
      builder.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutput(EXPECTED);
    }
  }

  public byte[] getTransformedClasses() throws Exception {
    return transformer(C.class).setPermittedSubclasses(C.class, Sub1.class, Sub2.class).transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      new Sub1();
      new Sub2();
      System.out.println("Success!");
    }
  }

  abstract static class C /* permits Sub1, Sub2 */ {}

  static class Sub1 extends C {}

  static class Sub2 extends C {}
}
