// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.methodhandles;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.methodhandles.MethodHandleTest.C;
import com.android.tools.r8.cf.methodhandles.MethodHandleTest.E;
import com.android.tools.r8.cf.methodhandles.MethodHandleTest.F;
import com.android.tools.r8.cf.methodhandles.MethodHandleTest.I;
import com.android.tools.r8.cf.methodhandles.MethodHandleTest.Impl;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MethodHandleTestRunner extends TestBase {
  static final Class<?> CLASS = MethodHandleTest.class;

  enum LookupType {
    DYNAMIC,
    CONSTANT,
  }

  private String getExpected() {
    return StringUtils.lines(
        "C 42", "svi 1", "sji 2", "svic 3", "sjic 4", "vvi 5", "vji 6", "vvic 7", "vjic 8", "svi 9",
        "sji 10", "svic 11", "sjic 12", "dvi 13", "dji 14", "dvic 15", "djic 16", "C 21", "true",
        "true");
  }

  private final TestParameters parameters;
  private final LookupType lookupType;

  @Parameters(name = "{0}, lookup:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        TestParameters.builder().withAllRuntimesAndApiLevels().build(), LookupType.values());
  }

  public MethodHandleTestRunner(TestParameters parameters, LookupType lookupType) {
    this.parameters = parameters;
    this.lookupType = lookupType;
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(getInputClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), CLASS.getName())
        .assertSuccessWithOutput(getExpected());
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .setMinApi(parameters)
        .addProgramClasses(getInputClasses())
        .addProgramClassFileData(getTransformedClasses())
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .run(parameters.getRuntime(), CLASS.getName())
        .apply(this::checkResult);
  }

  @Test
  public void testKeepAllR8() throws Exception {
    // For the dynamic case, method handles/types are created reflectively so keep all.
    runR8(TestShrinkerBuilder::addKeepAllClassesRule);
  }

  @Test
  public void testR8() throws Exception {
    // We can't shrink the DYNAMIC variant as the methods are looked up reflectively.
    assumeTrue(lookupType == LookupType.CONSTANT);
    runR8(b -> {});
  }

  private void runR8(ThrowableConsumer<R8FullTestBuilder> additionalSetUp) throws Exception {
    testForR8(parameters.getBackend())
        .apply(additionalSetUp)
        .setMinApi(parameters)
        .addProgramClasses(getInputClasses())
        .addProgramClassFileData(getTransformedClasses())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepMainRule(MethodHandleTest.class)
        .allowDiagnosticMessages()
        .compileWithExpectedDiagnostics(this::checkDiagnostics)
        .run(parameters.getRuntime(), CLASS.getCanonicalName())
        .apply(this::checkResult);
  }

  private boolean hasConstMethodCompileSupport() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithConstMethodHandleSupport());
  }

  private boolean hasInvokePolymorphicCompileSupport() {
    return parameters.isCfRuntime()
        || parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithInvokePolymorphicSupport());
  }

  private boolean hasMethodHandlesRuntimeSupport() {
    return parameters.isCfRuntime()
        || parameters
            .asDexRuntime()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithInvokePolymorphicSupport());
  }

  private void checkDiagnostics(TestDiagnosticMessages diagnostics) {
    if ((lookupType == LookupType.DYNAMIC && !hasInvokePolymorphicCompileSupport())
        || (lookupType == LookupType.CONSTANT && !hasConstMethodCompileSupport())) {
      diagnostics
          .assertAllWarningsMatch(diagnosticType(UnsupportedFeatureDiagnostic.class))
          .assertOnlyWarnings();
    } else {
      diagnostics.assertNoMessages();
    }
  }

  private void checkResult(TestRunResult<?> result) {
    if (lookupType == LookupType.DYNAMIC && !hasMethodHandlesRuntimeSupport()) {
      result
          .assertFailureWithErrorThatThrows(NoClassDefFoundError.class)
          .assertStderrMatches(containsString("java.lang.invoke.MethodHandles"));
      return;
    }
    if (lookupType == LookupType.DYNAMIC && !hasInvokePolymorphicCompileSupport()) {
      result
          .assertFailureWithErrorThatThrows(RuntimeException.class)
          .assertStderrMatches(containsString("invoke-polymorphic"));
      return;
    }
    if (lookupType == LookupType.CONSTANT && !hasConstMethodCompileSupport()) {
      result
          .assertFailureWithErrorThatThrows(RuntimeException.class)
          .assertStderrMatches(containsString("const-method-handle"));
      return;
    }
    result.assertSuccessWithOutput(getExpected());
  }

  private List<Class<?>> getInputClasses() {
    Builder<Class<?>> builder =
        ImmutableList.<Class<?>>builder().add(C.class, I.class, Impl.class, E.class, F.class);
    if (lookupType == LookupType.DYNAMIC) {
      builder.add(MethodHandleTest.class);
    }
    return builder.build();
  }

  private List<byte[]> getTransformedClasses() throws Exception {
    if (lookupType == LookupType.DYNAMIC) {
      return ImmutableList.of();
    }
    return ImmutableList.of(MethodHandleDump.getTransformedClass());
  }
}
