// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilationIf;
import static junit.framework.Assert.assertEquals;
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
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class MissingClassReferencedFromPermittedSubclassesAttributeTest
    extends MissingClassesTestBase {

  private static final DefinitionContext referencedFrom =
      DefinitionClassContextImpl.builder()
          .setClassContext(Reference.classFromClass(Super.class))
          .setOrigin(getOrigin(Super.class))
          .build();

  @Parameters(name = "{1}, report: {0}")
  public static List<Object[]> refinedData() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  private final boolean reportMissingClassesInPermittedSubclassesAttributes;

  public MissingClassReferencedFromPermittedSubclassesAttributeTest(
      boolean reportMissingClassesInPermittedSubclassesAttributes, TestParameters parameters) {
    super(parameters);
    this.reportMissingClassesInPermittedSubclassesAttributes =
        reportMissingClassesInPermittedSubclassesAttributes;
  }

  private void inspect(CodeInspector inspector) {
    // Missing classes stays in the PermittedSubclasses attribute.
    assertEquals(
        parameters.isCfRuntime()
            ? ImmutableList.of(
                inspector.clazz(Sub.class).asTypeSubject(),
                inspector.getTypeSubject(MissingSub.class.getTypeName()))
            : ImmutableList.of(),
        inspector.clazz(Super.class).getFinalPermittedSubclassAttributes());
  }

  @Test()
  public void testNoRules() throws Exception {
    assertFailsCompilationIf(
        reportMissingClassesInPermittedSubclassesAttributes,
        () ->
            compileWithExpectedDiagnostics(
                Main.class,
                reportMissingClassesInPermittedSubclassesAttributes
                    ? diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom)
                    : TestDiagnosticMessages::assertNoMessages,
                this::configure));
  }

  @Test
  public void testDontWarnSuperClass() throws Exception {
    compileWithExpectedDiagnostics(
            Main.class,
            TestDiagnosticMessages::assertNoMessages,
            addDontWarn(Super.class).andThen(this::configure))
        .inspect(this::inspect);
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
            Main.class,
            TestDiagnosticMessages::assertNoMessages,
            addDontWarn(MissingSub.class).andThen(this::configure))
        .inspect(this::inspect);
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
            Main.class,
            reportMissingClassesInPermittedSubclassesAttributes
                ? diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom)
                : TestDiagnosticMessages::assertNoMessages,
            addIgnoreWarnings(reportMissingClassesInPermittedSubclassesAttributes)
                .andThen(this::configure))
        .inspect(this::inspect);
  }

  void configure(R8FullTestBuilder builder) {
    try {
      builder
          .addKeepAttributePermittedSubclasses()
          .addProgramClasses(Sub.class)
          .addProgramClassFileData(getTransformedClasses())
          .addKeepClassRulesWithAllowObfuscation(Super.class, Sub.class)
          .addOptionsModification(
              options -> {
                // We do not report missing classes from permitted subclasses attributes by default.
                assertFalse(options.reportMissingClassesInPermittedSubclassesAttributes);
                options.reportMissingClassesInPermittedSubclassesAttributes =
                    reportMissingClassesInPermittedSubclassesAttributes;
              })
          .applyIf(
              !reportMissingClassesInPermittedSubclassesAttributes,
              // The -dontwarn Main and -dontwarn MissingClass tests will have unused -dontwarn
              // rules.
              R8TestBuilder::allowUnusedDontWarnPatterns);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  ClassReference getMissingClassReference() {
    return Reference.classFromClass(MissingSub.class);
  }

  public byte[] getTransformedClasses() throws Exception {
    return transformer(Super.class)
        .setPermittedSubclasses(Super.class, Sub.class, MissingSub.class)
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      new Sub();
      System.out.println("Success!");
    }
  }

  abstract static class Super /* permits Sub, MissingSub */ {}

  static class Sub extends Super {}

  static class MissingSub extends Super {}
}
