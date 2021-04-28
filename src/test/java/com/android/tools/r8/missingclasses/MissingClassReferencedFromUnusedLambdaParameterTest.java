// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilationIf;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionMethodContextImpl;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class MissingClassReferencedFromUnusedLambdaParameterTest extends MissingClassesTestBase {

  private static final DefinitionContext[] referencedFrom =
      new DefinitionContext[] {
        DefinitionMethodContextImpl.builder()
            .setMethodContext(
                Reference.method(
                    Reference.classFromClass(Main.class),
                    "lambda$main$0",
                    ImmutableList.of(Reference.classFromClass(MissingClass.class)),
                    null))
            .setOrigin(getOrigin(Main.class))
            .build(),
        DefinitionMethodContextImpl.builder()
            .setMethodContext(MethodReferenceUtils.mainMethod(Main.class))
            .setOrigin(getOrigin(Main.class))
            .build(),
      };

  public MissingClassReferencedFromUnusedLambdaParameterTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testNoRules() throws Exception {
    assertFailsCompilationIf(
        parameters.isCfRuntime(),
        () ->
            compileWithExpectedDiagnostics(
                Main.class,
                parameters.isCfRuntime()
                    ? diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom)
                    : TestDiagnosticMessages::assertNoMessages,
                addInterface()));
  }

  // The lambda is never called, therefore the lambda class' virtual method is dead, and therefore
  // we never trace into lambda$main$0(). Therefore, we need allowUnusedDontWarnPatterns() here.
  @Test
  public void testDontWarnMainClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addInterface()
            .andThen(
                builder ->
                    builder
                        .addDontWarn(Main.class)
                        .applyIf(
                            parameters.isDexRuntime(),
                            R8TestBuilder::allowUnusedDontWarnPatterns)));
  }

  // The lambda is never called, therefore the lambda class' virtual method is dead, and therefore
  // we never trace into lambda$main$0(). Therefore, we need allowUnusedDontWarnPatterns() here.
  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addInterface()
            .andThen(
                builder ->
                    builder
                        .addDontWarn(Main.class)
                        .applyIf(
                            parameters.isDexRuntime(),
                            R8TestBuilder::allowUnusedDontWarnPatterns)));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        parameters.isCfRuntime()
            ? diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom)
            : TestDiagnosticMessages::assertNoMessages,
        addInterface()
            .andThen(
                builder ->
                    builder
                        .addIgnoreWarnings()
                        .applyIf(
                            parameters.isCfRuntime(),
                            R8TestBuilder::allowDiagnosticWarningMessages)));
  }

  ThrowableConsumer<R8FullTestBuilder> addInterface() {
    return builder -> builder.addProgramClasses(I.class);
  }

  static class Main {

    public static void main(String[] args) {
      I ignore = mc -> {};
    }

    /* private static synthetic void lambda$main$0(MissingClass mc) {} */
  }

  interface I {

    void m(MissingClass mc);
  }
}
