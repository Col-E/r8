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

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
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
    // TODO (b/132676197): use desugaring handling
    Assume.assumeTrue(parameters.isDexRuntime());
    testIncompleteNestWarning(true);
    testMissingNestHostWarning(true);
  }

  @Test
  public void testWarningR8() throws Exception {
    // TODO (b/132676197): use desugaring handling
    // TODO (b/132676197): Cf backend should raise a warning
    // Remove Assume when fixed.
    Assume.assumeTrue(parameters.isDexRuntime());
    testIncompleteNestWarning(false);
    testMissingNestHostWarning(false);
  }

  @Test
  public void testErrorR8() {
    // TODO (b/132676197): Cf back should raise an error
    // TODO (b/132676197): Dex back-end should raise an error
    // Remove Assume when fixed.
    Assume.assumeTrue(false);
    testMissingNestHostError();
    testIncompleteNestError();
  }

  private TestCompileResult compileOnlyClassesMatching(
      Matcher<String> matcher, boolean d8, boolean ignoreMissingClasses) throws Exception {
    List<Path> matchingClasses =
        CLASS_NAMES.stream()
            .filter(matcher::matches)
            .map(name -> CLASSES_PATH.resolve(name + CLASS_EXTENSION))
            .collect(toList());
    if (d8) {
      return testForD8()
          .setMinApi(parameters.getApiLevel())
          .addProgramFiles(matchingClasses)
          .addOptionsModification(options -> options.enableNestBasedAccessDesugaring = true)
          .compile();
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
          .compile();
    }
  }

  private void testMissingNestHostError() {
    try {
      Matcher<String> innerClassMatcher =
          containsString("BasicNestHostWithInnerClassMethods$BasicNestedClass");
      compileOnlyClassesMatching(innerClassMatcher, false, false);
      fail("Should have raised an exception for missing nest host");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("requires its nest host"));
    }
  }

  private void testIncompleteNestError() {
    try {
      Matcher<String> innerClassMatcher = endsWith("BasicNestHostWithInnerClassMethods");
      compileOnlyClassesMatching(innerClassMatcher, false, false);
      fail("Should have raised an exception for incomplete nest");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("requires its nest mates"));
    }
  }

  private void testMissingNestHostWarning(boolean d8) throws Exception {
    Matcher<String> innerClassMatcher =
        containsString("BasicNestHostWithInnerClassMethods$BasicNestedClass");
    TestCompileResult compileResult = compileOnlyClassesMatching(innerClassMatcher, d8, true);
    assertTrue(compileResult.getDiagnosticMessages().getWarnings().size() >= 1);
    assertTrue(
        compileResult.getDiagnosticMessages().getWarnings().stream()
            .anyMatch(
                warning -> warning.getDiagnosticMessage().contains("requires its nest host")));
  }

  private void testIncompleteNestWarning(boolean d8) throws Exception {
    Matcher<String> innerClassMatcher = endsWith("BasicNestHostWithInnerClassMethods");
    TestCompileResult compileResult = compileOnlyClassesMatching(innerClassMatcher, d8, true);
    assertTrue(compileResult.getDiagnosticMessages().getWarnings().size() >= 1);
    assertTrue(
        compileResult.getDiagnosticMessages().getWarnings().stream()
            .anyMatch(
                warning -> warning.getDiagnosticMessage().contains("requires its nest mates")));
  }
}
