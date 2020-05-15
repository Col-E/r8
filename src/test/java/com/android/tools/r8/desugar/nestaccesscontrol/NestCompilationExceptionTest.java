// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.CLASSES_PATH;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.CLASS_NAMES;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.errors.IncompleteNestNestDesugarDiagnosic;
import com.android.tools.r8.errors.MissingNestHostNestDesugarDiagnostic;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Assume;
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
        .withAllApiLevels()
        .build();
  }

  @Test
  public void testWarningD8() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testIncompleteNestWarning(true, true);
    testMissingNestHostWarning(true, true);
  }

  @Test
  public void testWarningR8() throws Exception {
    testIncompleteNestWarning(false, parameters.isDexRuntime());
    testMissingNestHostWarning(false, parameters.isDexRuntime());
  }

  @Test
  public void testErrorR8() {
    testMissingNestHostError();
    testIncompleteNestError();
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
      return testForD8()
          .setMinApi(parameters.getApiLevel())
          .addProgramFiles(matchingClasses)
          .addOptionsModification(options -> options.enableNestBasedAccessDesugaring = true);
    } else {
      return testForR8(parameters.getBackend())
          .noTreeShaking()
          .noMinification()
          .addKeepAllAttributes()
          .setMinApi(parameters.getApiLevel())
          .addProgramFiles(matchingClasses)
          .addOptionsModification(
              options -> {
                options.enableNestBasedAccessDesugaring = true;
                options.ignoreMissingClasses = ignoreMissingClasses;
              })
          .allowDiagnosticWarningMessages(allowDiagnosticWarningMessages);
    }
  }

  private void testMissingNestHostError() {
    try {
      Matcher<String> innerClassMatcher =
          containsString("BasicNestHostWithInnerClassMethods$BasicNestedClass");
      compileOnlyClassesMatching(innerClassMatcher, false, false, false)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertErrorMessageThatMatches(containsString("requires its nest host"));
              });
    } catch (CompilationFailedException e) {
      // Expected failure.
      return;
    }
    fail("Should have raised an exception for missing nest host");
  }

  private void testIncompleteNestError() {
    try {
      Matcher<String> innerClassMatcher = endsWith("BasicNestHostWithInnerClassMethods");
      compileOnlyClassesMatching(innerClassMatcher, false, false, false)
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                diagnostics.assertErrorMessageThatMatches(
                    containsString("requires its nest mates"));
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
    if (desugarWarning) {
      assertTrue(
          compileResult.getDiagnosticMessages().getWarnings().stream()
              .anyMatch(warn -> warn instanceof MissingNestHostNestDesugarDiagnostic));
    } else if (!d8) {
      // R8 should raise extra warning when cleaning the nest.
      assertTrue(
          compileResult.getDiagnosticMessages().getWarnings().stream()
              .anyMatch(warn -> warn.getDiagnosticMessage().contains("requires its nest host")));
    }
  }

  private void testIncompleteNestWarning(boolean d8, boolean desugarWarning) throws Exception {
    Matcher<String> innerClassMatcher = endsWith("BasicNestHostWithInnerClassMethods");
    TestCompileResult<?, ?> compileResult =
        compileOnlyClassesMatching(innerClassMatcher, d8, !d8, true).compile();
    assertTrue(compileResult.getDiagnosticMessages().getWarnings().size() >= 1);
    if (desugarWarning) {
      assertTrue(
          compileResult.getDiagnosticMessages().getWarnings().stream()
              .anyMatch(warn -> warn instanceof IncompleteNestNestDesugarDiagnosic));
    } else if (!d8) {
      // R8 should raise extra warning when cleaning the nest.
      compileResult.assertWarningMessageThatMatches(containsString("requires its nest mates"));
    }
  }
}
