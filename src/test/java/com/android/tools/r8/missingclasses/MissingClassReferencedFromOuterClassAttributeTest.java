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
import com.android.tools.r8.missingclasses.MissingClassReferencedFromOuterClassAttributeTest.MissingClass.Main;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class MissingClassReferencedFromOuterClassAttributeTest extends MissingClassesTestBase {

  private static final DefinitionContext referencedFrom =
      DefinitionClassContextImpl.builder()
          .setClassContext(Reference.classFromClass(Main.class))
          .setOrigin(getOrigin(Main.class))
          .build();

  @Parameters(name = "{1}, report: {0}")
  public static List<Object[]> refinedData() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  private final boolean reportMissingClassesInInnerClassAttributes;

  public MissingClassReferencedFromOuterClassAttributeTest(
      boolean reportMissingClassesInInnerClassAttributes, TestParameters parameters) {
    super(parameters);
    this.reportMissingClassesInInnerClassAttributes = reportMissingClassesInInnerClassAttributes;
  }

  @Test
  public void testNoRules() throws Exception {
    assertFailsCompilationIf(
        reportMissingClassesInInnerClassAttributes,
        () ->
            compileWithExpectedDiagnostics(
                Main.class,
                reportMissingClassesInInnerClassAttributes
                    ? diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom)
                    : TestDiagnosticMessages::assertNoMessages,
                this::configure));
  }

  @Test
  public void testDontWarnMainClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addDontWarn(Main.class).andThen(this::configure));
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addDontWarn(MissingClass.class).andThen(this::configure));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        reportMissingClassesInInnerClassAttributes
            ? diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom)
            : TestDiagnosticMessages::assertNoMessages,
        addIgnoreWarnings(reportMissingClassesInInnerClassAttributes).andThen(this::configure));
  }

  void configure(R8FullTestBuilder builder) {
    builder
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addOptionsModification(
            options -> {
              // We do not report missing classes from inner class attributes by default.
              assertFalse(options.reportMissingClassesInInnerClassAttributes);
              options.reportMissingClassesInInnerClassAttributes =
                  reportMissingClassesInInnerClassAttributes;
            })
        .applyIf(
            reportMissingClassesInInnerClassAttributes,
            // We need to ignore the inner class attribute for the test class.
            addDontWarn(MissingClassReferencedFromOuterClassAttributeTest.class),
            // The -dontwarn Main and -dontwarn MissingClass tests will have unused -dontwarn rules.
            R8TestBuilder::allowUnusedDontWarnPatterns);
  }

  @Override
  ClassReference getMissingClassReference() {
    return Reference.classFromClass(MissingClass.class);
  }

  static class MissingClass {

    static class Main {

      public static void main(String[] args) {}
    }
  }
}
