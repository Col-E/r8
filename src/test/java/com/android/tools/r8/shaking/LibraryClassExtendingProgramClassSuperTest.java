// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryClassExtendingProgramClassSuperTest extends TestBase {

  private final TestParameters parameters;
  private final boolean proguardCompatibility;
  private final boolean dontWarn;
  private final String EXPECTED = ProgramIndirectSuper.class.getTypeName();

  @Parameters(name = "{0}, proguardcompatiblity: {1}, dontwarn: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public LibraryClassExtendingProgramClassSuperTest(
      TestParameters parameters, boolean proguardCompatibility, boolean dontWarn) {
    this.parameters = parameters;
    this.proguardCompatibility = proguardCompatibility;
    this.dontWarn = dontWarn;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, ProgramDirectSuper.class, ProgramIndirectSuper.class)
        .addRunClasspathFiles(
            compileToZip(
                parameters,
                ImmutableList.of(ProgramDirectSuper.class, ProgramIndirectSuper.class),
                LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    R8TestBuilder<? extends R8TestBuilder<?>> r8TestBuilder =
        (proguardCompatibility
                ? testForR8Compat(parameters.getBackend(), true)
                : testForR8(parameters.getBackend()))
            .addLibraryFiles(
                parameters.isCfRuntime()
                    ? ToolHelper.getJava8RuntimeJar()
                    : ToolHelper.getAndroidJar(parameters.getApiLevel()))
            .addLibraryClasses(LibraryClass.class)
            .addProgramClasses(ProgramDirectSuper.class, ProgramIndirectSuper.class, Main.class)
            .addKeepMainRule(Main.class)
            .applyIf(dontWarn, b -> b.addDontWarn(LibraryClass.class))
            .setMinApi(parameters)
            .addRunClasspathFiles(
                compileToZip(
                    parameters,
                    ImmutableList.of(ProgramIndirectSuper.class, ProgramDirectSuper.class),
                    LibraryClass.class));
    if (proguardCompatibility && dontWarn) {
      r8TestBuilder.run(parameters.getRuntime(), Main.class).assertSuccessWithOutputLines(EXPECTED);
    } else if (proguardCompatibility) {
      r8TestBuilder
          .allowDiagnosticWarningMessages()
          .compile()
          .assertAllWarningMessagesMatch(
              containsString(
                  LibraryClass.class.getTypeName()
                      + " extends program class "
                      + ProgramDirectSuper.class.getTypeName()))
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutputLines(ProgramIndirectSuper.class.getTypeName());
    } else if (dontWarn) {
      // TODO(b/159609181): This fails with an assertion error, should we not handle it the same as
      //   proguardcompat?
      assertThrows(
          CompilationFailedException.class,
          () ->
              r8TestBuilder.compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics.assertErrorsMatch(
                          diagnosticMessage(
                              containsString(
                                  "Failed lookup of non-missing type: "
                                      + ProgramDirectSuper.class.getTypeName())))));
    } else {
      assertThrows(
          CompilationFailedException.class,
          () ->
              r8TestBuilder.compileWithExpectedDiagnostics(
                  diagnostics -> {
                    diagnostics.assertErrorsMatch(
                        diagnosticMessage(
                            containsString(
                                LibraryClass.class.getTypeName()
                                    + " extends program class "
                                    + ProgramDirectSuper.class.getTypeName())));
                  }));
    }
  }

  public static class ProgramIndirectSuper {}

  public static class ProgramDirectSuper extends ProgramIndirectSuper {}

  public static class LibraryClass extends ProgramDirectSuper {

    void foo() {
      System.out.println(this.getClass().getSuperclass().getSuperclass().getName());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new LibraryClass().foo();
    }
  }
}
