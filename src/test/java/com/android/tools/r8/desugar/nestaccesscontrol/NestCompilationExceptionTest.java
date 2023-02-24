// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.CLASSES_PATH;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.CLASS_NAMES;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Jdk9TestUtils;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.diagnostic.internal.MissingDefinitionsDiagnosticImpl;
import com.android.tools.r8.errors.IncompleteNestNestDesugarDiagnosic;
import com.android.tools.r8.errors.InterfaceDesugarMissingTypeDiagnostic;
import com.android.tools.r8.errors.MissingNestHostNestDesugarDiagnostic;
import com.android.tools.r8.references.Reference;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestCompilationExceptionTest extends TestBase {

  public NestCompilationExceptionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntime(DexVm.Version.first())
        .withDexRuntime(DexVm.Version.last())
        .withApiLevelsStartingAtIncluding(apiLevelWithInvokeCustomSupport())
        .enableApiLevelsForCf()
        .build();
  }

  @Test
  public void testD8() {
    testMissingNestHostError(true);
    testIncompleteNestError(true);
  }

  @Test
  public void testWarningR8() throws Exception {
    parameters.assumeR8TestParameters();
    testIncompleteNestWarning(false, parameters.isDexRuntime());
    testMissingNestHostWarning(false, parameters.isDexRuntime());
  }

  @Test
  public void testErrorR8() {
    parameters.assumeR8TestParameters();
    testMissingNestHostError(false);
    testIncompleteNestError(false);
  }

  private TestCompilerBuilder<?, ?, ?, ?, ?> compileOnlyClassesMatching(
      Matcher<String> matcher,
      boolean d8,
      boolean allowDiagnosticWarningMessages,
      boolean ignoreMissingClasses) {
    List<Path> matchingClasses =
        CLASS_NAMES.stream()
            .filter(matcher::matches)
            .map(name -> CLASSES_PATH.resolve(name + CLASS_EXTENSION))
            .collect(toList());
    if (d8) {
      return testForD8(parameters.getBackend())
          .setMinApi(parameters)
          .addProgramFiles(matchingClasses);
    } else {
      return testForR8(parameters.getBackend())
          .noTreeShaking()
          .addDontObfuscate()
          .addKeepAllAttributes()
          .setMinApi(parameters)
          .addProgramFiles(matchingClasses)
          .applyIf(parameters.isCfRuntime(), Jdk9TestUtils.addJdk9LibraryFiles(temp))
          .addIgnoreWarnings(ignoreMissingClasses)
          .allowDiagnosticWarningMessages(allowDiagnosticWarningMessages);
    }
  }

  private void testMissingNestHostError(boolean d8) {
    try {
      Matcher<String> innerClassMatcher =
          containsString("BasicNestHostWithInnerClassMethods$BasicNestedClass");
      compileOnlyClassesMatching(innerClassMatcher, d8, false, false)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                if (d8) {
                  if (parameters.getBackend().isDex()) {
                    diagnostics.assertOnlyErrors();
                  }
                  diagnostics.assertErrorsMatch(
                      diagnosticType(MissingNestHostNestDesugarDiagnostic.class));

                  MissingNestHostNestDesugarDiagnostic diagnostic =
                      (MissingNestHostNestDesugarDiagnostic) diagnostics.getErrors().get(0);
                  assertEquals(
                      "Class BasicNestHostWithInnerClassMethods$BasicNestedClass requires its nest "
                          + "host BasicNestHostWithInnerClassMethods to be on program or class "
                          + "path.",
                      diagnostic.getDiagnosticMessage());
                } else {
                  diagnostics
                      .assertOnlyErrors()
                      .inspectErrors(
                          diagnostic ->
                              diagnostic
                                  .assertIsMissingDefinitionsDiagnostic()
                                  .assertIsMissingClass(
                                      Reference.classFromTypeName(
                                          "nesthostexample.BasicNestHostWithInnerClassMethods"))
                                  .assertNumberOfMissingClasses(1));
                }
              });
    } catch (CompilationFailedException e) {
      // Expected failure.
      return;
    }
    fail("Should have raised an exception for missing nest host");
  }

  private void testIncompleteNestError(boolean d8) {
    try {
      Matcher<String> innerClassMatcher = endsWith("BasicNestHostWithInnerClassMethods");
      compileOnlyClassesMatching(innerClassMatcher, d8, false, false)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                if (d8) {
                  if (parameters.getBackend().isDex()) {
                    diagnostics.assertOnlyErrors();
                  }
                  diagnostics.assertErrorsMatch(
                      diagnosticType(IncompleteNestNestDesugarDiagnosic.class));

                  IncompleteNestNestDesugarDiagnosic diagnostic =
                      (IncompleteNestNestDesugarDiagnosic) diagnostics.getErrors().get(0);
                  assertEquals(
                      "Compilation of classes nesthostexample.BasicNestHostWithInnerClassMethods "
                          + "requires its nest mates "
                          + "nesthostexample.BasicNestHostWithInnerClassMethods$BasicNestedClass "
                          + "(unavailable) to be on program or class path.",
                      diagnostic.getDiagnosticMessage());
                } else {
                  diagnostics
                      .assertOnlyErrors()
                      .inspectErrors(
                          diagnostic ->
                              diagnostic
                                  .assertIsMissingDefinitionsDiagnostic()
                                  .assertIsMissingClass(
                                      Reference.classFromTypeName(
                                          "nesthostexample.BasicNestHostWithInnerClassMethods$BasicNestedClass"))
                                  .assertNumberOfMissingClasses(1));
                }
              });
    } catch (Exception e) {
      // Expected failure.
      return;
    }
    fail("Should have raised an exception for incomplete nest");
  }

  private void testMissingNestHostWarning(boolean d8, boolean desugarWarning) throws Exception {
    Matcher<String> innerClassMatcher =
        containsString("BasicNestHostWithInnerClassMethods$BasicNestedClass");
    TestCompileResult<?, ?> compileResult =
        compileOnlyClassesMatching(innerClassMatcher, d8, !d8, true).compile();
    assertTrue(compileResult.getDiagnosticMessages().getWarnings().size() >= 1);
    if (d8 && desugarWarning) {
      assertTrue(
          compileResult.getDiagnosticMessages().getWarnings().stream()
              .anyMatch(warn -> warn instanceof MissingNestHostNestDesugarDiagnostic));
    }
    if (!d8) {
      // R8 should raise extra warning when cleaning the nest.
      compileResult.inspectDiagnosticMessages(
          diagnostics -> {
            diagnostics.assertOnlyWarnings();
            if (parameters.isCfRuntime() || parameters.canUseDefaultAndStaticInterfaceMethods()) {
              diagnostics.assertWarningsMatch(
                  diagnosticType(MissingDefinitionsDiagnosticImpl.class));
            } else {
              diagnostics.assertWarningsMatch(
                  diagnosticType(MissingDefinitionsDiagnosticImpl.class),
                  diagnosticType(InterfaceDesugarMissingTypeDiagnostic.class));
            }
            diagnostics.inspectWarning(
                0,
                diagnostic ->
                    diagnostic
                        .assertIsMissingDefinitionsDiagnostic()
                        .assertIsMissingClass(
                            Reference.classFromTypeName(
                                "nesthostexample.BasicNestHostWithInnerClassMethods"))
                        .assertNumberOfMissingClasses(1));
          });
    }
  }

  private void testIncompleteNestWarning(boolean d8, boolean desugarWarning) throws Exception {
    Matcher<String> innerClassMatcher = endsWith("BasicNestHostWithInnerClassMethods");
    TestCompileResult<?, ?> compileResult =
        compileOnlyClassesMatching(innerClassMatcher, d8, !d8, true).compile();
    assertTrue(compileResult.getDiagnosticMessages().getWarnings().size() >= 1);
    if (d8 && desugarWarning) {
      assertTrue(
          compileResult.getDiagnosticMessages().getWarnings().stream()
              .anyMatch(warn -> warn instanceof IncompleteNestNestDesugarDiagnosic));
    }
    if (!d8) {
      // R8 should raise extra warning when cleaning the nest.
      compileResult.inspectDiagnosticMessages(
          diagnostics -> {
            diagnostics.assertOnlyWarnings();
            if (parameters.isCfRuntime() || parameters.canUseDefaultAndStaticInterfaceMethods()) {
              diagnostics.assertWarningsMatch(
                  diagnosticType(MissingDefinitionsDiagnosticImpl.class));
            } else {
              diagnostics.assertWarningsMatch(
                  diagnosticType(MissingDefinitionsDiagnosticImpl.class),
                  diagnosticType(InterfaceDesugarMissingTypeDiagnostic.class));
            }
            diagnostics.inspectWarning(
                0,
                diagnostic ->
                    diagnostic
                        .assertIsMissingDefinitionsDiagnostic()
                        .assertIsMissingClass(
                            Reference.classFromTypeName(
                                "nesthostexample.BasicNestHostWithInnerClassMethods$BasicNestedClass"))
                        .assertNumberOfMissingClasses(1));
          });
    }
  }
}
