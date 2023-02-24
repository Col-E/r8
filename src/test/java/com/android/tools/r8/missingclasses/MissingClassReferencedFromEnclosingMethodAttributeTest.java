// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilationIf;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionClassContextImpl;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class MissingClassReferencedFromEnclosingMethodAttributeTest extends MissingClassesTestBase {

  private static final DefinitionContext referencedFrom =
      DefinitionClassContextImpl.builder()
          .setClassContext(Reference.classFromClass(getMainClass()))
          .setOrigin(getOrigin(getMainClass()))
          .build();

  @Parameters(name = "{1}, report: {0}")
  public static List<Object[]> refinedData() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  private final boolean reportMissingClassesInEnclosingMethodAttribute;

  public MissingClassReferencedFromEnclosingMethodAttributeTest(
      boolean reportMissingClassesInEnclosingMethodAttribute, TestParameters parameters) {
    super(parameters);
    this.reportMissingClassesInEnclosingMethodAttribute =
        reportMissingClassesInEnclosingMethodAttribute;
  }

  @Test
  public void testNoRules() throws Exception {
    assertFailsCompilationIf(
        reportMissingClassesInEnclosingMethodAttribute,
        () ->
            compileWithExpectedDiagnostics(
                getMainClass(),
                reportMissingClassesInEnclosingMethodAttribute
                    ? diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom)
                    : TestDiagnosticMessages::assertNoMessages,
                this::configure));
  }

  @Test
  public void testDontWarnMainClass() throws Exception {
    compileWithExpectedDiagnostics(
        getMainClass(),
        TestDiagnosticMessages::assertNoMessages,
        addDontWarn(getMainClass()).andThen(this::configure));
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        getMainClass(),
        TestDiagnosticMessages::assertNoMessages,
        addDontWarn(MissingClass.class).andThen(this::configure));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        getMainClass(),
        reportMissingClassesInEnclosingMethodAttribute
            ? diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom)
            : TestDiagnosticMessages::assertNoMessages,
        addIgnoreWarnings(reportMissingClassesInEnclosingMethodAttribute).andThen(this::configure));
  }

  void configure(R8FullTestBuilder builder) {
    builder
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addOptionsModification(
            options -> {
              // We do not report missing classes from inner class attributes by default.
              assertFalse(options.reportMissingClassesInEnclosingMethodAttribute);
              options.reportMissingClassesInEnclosingMethodAttribute =
                  reportMissingClassesInEnclosingMethodAttribute;
            })
        .applyIf(
            !reportMissingClassesInEnclosingMethodAttribute,
            // The -dontwarn Main and -dontwarn MissingClass tests will have unused -dontwarn rules.
            R8TestBuilder::allowUnusedDontWarnPatterns);
  }

  static Class<?> getMainClass() {
    return MissingClass.getMainClass();
  }

  @Override
  ClassReference getMissingClassReference() {
    return Reference.classFromClass(MissingClass.class);
  }

  static class MissingClass {

    static Class<?> getMainClass() {
      class Main {}
      return Main.class;
    }
  }
}
