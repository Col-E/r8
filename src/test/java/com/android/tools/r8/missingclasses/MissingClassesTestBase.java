// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder.DiagnosticsConsumer;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.MissingDefinitionsDiagnosticTestUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class MissingClassesTestBase extends TestBase {

  static class MissingClass extends RuntimeException {

    static int FIELD;

    int field;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface MissingRuntimeAnnotation {}

  interface MissingInterface {}

  interface MissingFunctionalInterface {

    void m();
  }

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MissingClassesTestBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  public ThrowableConsumer<R8FullTestBuilder> addDontWarn(Class<?>... classes) {
    return builder -> builder.addDontWarn(classes);
  }

  public ThrowableConsumer<R8FullTestBuilder> addIgnoreWarnings() {
    return addIgnoreWarnings(true);
  }

  public ThrowableConsumer<R8FullTestBuilder> addIgnoreWarnings(
      boolean allowDiagnosticWarningMessages) {
    return builder ->
        builder.addIgnoreWarnings().allowDiagnosticWarningMessages(allowDiagnosticWarningMessages);
  }

  public void compileWithExpectedDiagnostics(
      Class<?> mainClass, DiagnosticsConsumer diagnosticsConsumer)
      throws CompilationFailedException {
    compileWithExpectedDiagnostics(mainClass, diagnosticsConsumer, null);
  }

  public R8TestCompileResult compileWithExpectedDiagnostics(
      Class<?> mainClass,
      DiagnosticsConsumer diagnosticsConsumer,
      ThrowableConsumer<R8FullTestBuilder> configuration)
      throws CompilationFailedException {
    return internalCompileWithExpectedDiagnostics(
        diagnosticsConsumer,
        builder ->
            builder.addProgramClasses(mainClass).addKeepMainRule(mainClass).apply(configuration));
  }

  public R8TestCompileResult compileWithExpectedDiagnostics(
      ThrowableConsumer<R8FullTestBuilder> configuration, DiagnosticsConsumer diagnosticsConsumer)
      throws CompilationFailedException {
    return internalCompileWithExpectedDiagnostics(diagnosticsConsumer, configuration);
  }

  private R8TestCompileResult internalCompileWithExpectedDiagnostics(
      DiagnosticsConsumer diagnosticsConsumer, ThrowableConsumer<R8FullTestBuilder> configuration)
      throws CompilationFailedException {
    return testForR8(parameters.getBackend())
        .apply(configuration)
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(diagnosticsConsumer);
  }

  ClassReference getMissingClassReference() {
    return Reference.classFromClass(MissingClass.class);
  }

  void inspectDiagnosticsWithIgnoreWarnings(
      TestDiagnosticMessages diagnostics, DefinitionContext... referencedFrom) {
    assertTrue(referencedFrom.length > 0);
    inspectDiagnosticsWithIgnoreWarnings(
        diagnostics,
        referencedFrom,
        MissingDefinitionsDiagnosticTestUtils.getMissingClassMessage(
            getMissingClassReference(), referencedFrom));
  }

  void inspectDiagnosticsWithIgnoreWarnings(
      TestDiagnosticMessages diagnostics,
      DefinitionContext[] referencedFrom,
      String expectedDiagnosticMessage) {
    diagnostics
        .assertOnlyWarnings()
        .inspectWarnings(
            diagnostic ->
                diagnostic
                    .assertIsMissingDefinitionsDiagnostic()
                    .applyIf(
                        referencedFrom != null,
                        checker ->
                            checker.assertIsMissingClassWithExactContexts(
                                getMissingClassReference(), referencedFrom),
                        checker -> checker.assertIsMissingClass(getMissingClassReference()))
                    .assertHasMessage(expectedDiagnosticMessage)
                    .assertNumberOfMissingClasses(1));
  }

  void inspectDiagnosticsWithNoRules(
      TestDiagnosticMessages diagnostics, DefinitionContext... referencedFrom) {
    assertTrue(referencedFrom.length > 0);
    inspectDiagnosticsWithNoRules(
        diagnostics,
        referencedFrom,
        MissingDefinitionsDiagnosticTestUtils.getMissingClassMessage(
            getMissingClassReference(), referencedFrom));
  }

  void inspectDiagnosticsWithNoRules(
      TestDiagnosticMessages diagnostics,
      DefinitionContext[] referencedFrom,
      String expectedDiagnosticMessage) {
    diagnostics
        .assertOnlyErrors()
        .inspectErrors(
            diagnostic ->
                diagnostic
                    .assertIsMissingDefinitionsDiagnostic()
                    .applyIf(
                        referencedFrom != null,
                        checker ->
                            checker.assertIsMissingClassWithExactContexts(
                                getMissingClassReference(), referencedFrom),
                        checker -> checker.assertIsMissingClass(getMissingClassReference()))
                    .assertHasMessage(expectedDiagnosticMessage)
                    .assertNumberOfMissingClasses(1));
  }
}
