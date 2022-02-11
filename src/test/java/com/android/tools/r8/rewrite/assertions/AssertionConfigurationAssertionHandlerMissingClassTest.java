// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.rewrite.assertions.assertionhandler.AssertionHandlers;
import com.android.tools.r8.rewrite.assertions.assertionhandler.AssertionsSimple;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssertionConfigurationAssertionHandlerMissingClassTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "assertionHandler: simpleAssertion",
          "assertionHandler: multipleAssertions",
          "assertionHandler: multipleAssertions");
  private static Class<?> MAIN_CLASS = AssertionsSimple.class;

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  private MethodReference getAssertionHandler() {
    try {
      return Reference.methodFromMethod(
          AssertionHandlers.class.getMethod("assertionHandler", Throwable.class));
    } catch (NoSuchMethodException e) {
    }
    fail();
    return null;
  }

  private Path jarWithAssertionHandler() throws Exception {
    Path jar = temp.newFolder().toPath().resolve("assertion_handler.jar");
    if (parameters.isDexRuntime()) {
      testForD8()
          .addProgramClasses(AssertionHandlers.class)
          .setMinApi(parameters.getApiLevel())
          .compile()
          .writeToZip(jar);
      return jar;
    } else {
      return ZipBuilder.builder(jar)
          .addFilesRelative(
              ToolHelper.getClassPathForTests(),
              ToolHelper.getClassFileForTestClass(AssertionHandlers.class))
          .build();
    }
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(MAIN_CLASS)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepAnnotation()
        .addKeepRules("-keepclassmembers class * { @com.android.tools.r8.Keep *; }")
        .setMinApi(parameters.getApiLevel())
        .addIgnoreWarnings()
        .addAssertionsConfiguration(
            builder -> builder.setAssertionHandler(getAssertionHandler()).setScopeAll().build())
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyWarnings()
                    .inspectWarnings(
                        diagnostic ->
                            diagnostic
                                .assertIsMissingDefinitionsDiagnostic()
                                .assertIsAllMissingClasses(AssertionHandlers.class)))
        .addRunClasspathFiles(jarWithAssertionHandler())
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}
