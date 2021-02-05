// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder.DiagnosticsConsumer;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.MissingClassesDiagnostic;
import com.android.tools.r8.utils.FieldReferenceUtils;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.function.Function;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class MissingClassesTestBase extends TestBase {

  static class MissingClass extends RuntimeException {

    static int FIELD;

    int field;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public MissingClassesTestBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  public ThrowableConsumer<R8FullTestBuilder> addDontWarn(Class<?> clazz) {
    return builder -> builder.addDontWarn(clazz);
  }

  public ThrowableConsumer<R8FullTestBuilder> addIgnoreWarnings() {
    return builder -> builder.addIgnoreWarnings().allowDiagnosticWarningMessages();
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
    testForR8(parameters.getBackend())
        .addProgramClasses(mainClass)
        .addKeepMainRule(mainClass)
        .apply(configuration)
        .addOptionsModification(TestingOptions::enableExperimentalMissingClassesReporting)
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(diagnosticsConsumer);
  }

  void inspectDiagnosticsWithIgnoreWarnings(
      TestDiagnosticMessages diagnostics, FieldReference referencedFrom) {
    inspectDiagnosticsWithIgnoreWarnings(
        diagnostics,
        getExpectedDiagnosticMessageWithIgnoreWarnings(
            referencedFrom, FieldReferenceUtils::toSourceString));
  }

  void inspectDiagnosticsWithIgnoreWarnings(
      TestDiagnosticMessages diagnostics, MethodReference referencedFrom) {
    inspectDiagnosticsWithIgnoreWarnings(
        diagnostics,
        getExpectedDiagnosticMessageWithIgnoreWarnings(
            referencedFrom, MethodReferenceUtils::toSourceString));
  }

  void inspectDiagnosticsWithIgnoreWarnings(
      TestDiagnosticMessages diagnostics, String expectedDiagnosticMessage) {
    MissingClassesDiagnostic diagnostic =
        diagnostics
            .assertOnlyWarnings()
            .assertWarningsCount(1)
            .assertAllWarningsMatch(diagnosticType(MissingClassesDiagnostic.class))
            .getWarning(0);
    assertEquals(
        ImmutableSet.of(Reference.classFromClass(MissingClass.class)),
        diagnostic.getMissingClasses());
    assertEquals(expectedDiagnosticMessage, diagnostic.getDiagnosticMessage());
  }

  private <T> String getExpectedDiagnosticMessageWithIgnoreWarnings(
      T referencedFrom, Function<T, String> toSourceStringFunction) {
    return "Missing class "
        + MissingClass.class.getTypeName()
        + " (referenced from: "
        + toSourceStringFunction.apply(referencedFrom)
        + ")";
  }

  void inspectDiagnosticsWithNoRules(
      TestDiagnosticMessages diagnostics, FieldReference referencedFrom) {
    inspectDiagnosticsWithNoRules(
        diagnostics,
        getExpectedDiagnosticMessageWithNoRules(
            referencedFrom, FieldReferenceUtils::toSourceString));
  }

  void inspectDiagnosticsWithNoRules(
      TestDiagnosticMessages diagnostics, MethodReference referencedFrom) {
    inspectDiagnosticsWithNoRules(
        diagnostics,
        getExpectedDiagnosticMessageWithNoRules(
            referencedFrom, MethodReferenceUtils::toSourceString));
  }

  void inspectDiagnosticsWithNoRules(
      TestDiagnosticMessages diagnostics, String expectedDiagnosticMessage) {
    MissingClassesDiagnostic diagnostic =
        diagnostics
            .assertOnlyErrors()
            .assertErrorsCount(1)
            .assertAllErrorsMatch(diagnosticType(MissingClassesDiagnostic.class))
            .getError(0);
    assertEquals(
        ImmutableSet.of(Reference.classFromClass(MissingClass.class)),
        diagnostic.getMissingClasses());
    assertEquals(expectedDiagnosticMessage, diagnostic.getDiagnosticMessage());
  }

  private <T> String getExpectedDiagnosticMessageWithNoRules(
      T referencedFrom, Function<T, String> toSourceStringFunction) {
    return "Compilation can't be completed because the following class is missing: "
        + MissingClass.class.getTypeName()
        + " (referenced from: "
        + toSourceStringFunction.apply(referencedFrom)
        + ")"
        + ".";
  }
}
