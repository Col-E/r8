// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder.DiagnosticsConsumer;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionContextUtils;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
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

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
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

  public void compileWithExpectedDiagnostics(
      Class<?> mainClass,
      DiagnosticsConsumer diagnosticsConsumer,
      ThrowableConsumer<R8FullTestBuilder> configuration)
      throws CompilationFailedException {
    internalCompileWithExpectedDiagnostics(
        diagnosticsConsumer,
        builder ->
            builder.addProgramClasses(mainClass).addKeepMainRule(mainClass).apply(configuration));
  }

  public void compileWithExpectedDiagnostics(
      ThrowableConsumer<R8FullTestBuilder> configuration, DiagnosticsConsumer diagnosticsConsumer)
      throws CompilationFailedException {
    internalCompileWithExpectedDiagnostics(diagnosticsConsumer, configuration);
  }

  private void internalCompileWithExpectedDiagnostics(
      DiagnosticsConsumer diagnosticsConsumer, ThrowableConsumer<R8FullTestBuilder> configuration)
      throws CompilationFailedException {
    testForR8(parameters.getBackend())
        .apply(configuration)
        .setMinApi(parameters.getApiLevel())
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
        getExpectedDiagnosticMessage(
            DefinitionContextUtils.toSourceString(referencedFrom[0]), referencedFrom.length));
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
        getExpectedDiagnosticMessage(
            DefinitionContextUtils.toSourceString(referencedFrom[0]), referencedFrom.length));
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

  private String getExpectedDiagnosticMessage(String referencedFrom, int numberOfContexts) {
    StringBuilder builder =
        new StringBuilder("Missing class ")
            .append(getMissingClassReference().getTypeName())
            .append(" (referenced from: ")
            .append(referencedFrom);
    if (numberOfContexts > 1) {
      builder.append(" and ").append(numberOfContexts - 1).append(" other context");
      if (numberOfContexts > 2) {
        builder.append("s");
      }
    }
    return builder.append(")").toString();
  }
}
