// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionClassContextImpl;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import org.junit.Test;

public class MissingClassReferencedFromForcefullyMovedMethodTest extends MissingClassesTestBase {

  private static final DefinitionContext referencedFrom =
      DefinitionClassContextImpl.builder()
          .setClassContext(Reference.classFromClass(I.class))
          .setOrigin(getOrigin(I.class))
          .build();

  public MissingClassReferencedFromForcefullyMovedMethodTest(TestParameters parameters) {
    super(parameters);
    assumeFalse(parameters.canUseDefaultAndStaticInterfaceMethods());
  }

  @Test(expected = CompilationFailedException.class)
  public void testNoRules() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom),
        addInterface());
  }

  @Test
  public void testDontWarnMainClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addInterface().andThen(addDontWarn(I.class)));
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addInterface().andThen(addDontWarn(MissingFunctionalInterface.class)));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom),
        addInterface().andThen(addIgnoreWarnings()));
  }

  @Override
  ClassReference getMissingClassReference() {
    return Reference.classFromClass(MissingFunctionalInterface.class);
  }

  private ThrowableConsumer<R8FullTestBuilder> addInterface() {
    return builder -> builder.addProgramClasses(I.class);
  }

  static class Main {

    public static void main(String[] args) {
      I.forcefullyMovedMethod();
    }
  }

  interface I {

    static void forcefullyMovedMethod() {
      MissingFunctionalInterface i = () -> {};
    }
  }
}
