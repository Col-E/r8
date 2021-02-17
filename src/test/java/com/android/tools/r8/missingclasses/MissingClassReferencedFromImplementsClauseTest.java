// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.diagnostic.MissingDefinitionContext;
import com.android.tools.r8.diagnostic.internal.MissingDefinitionClassContext;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import org.junit.Test;

public class MissingClassReferencedFromImplementsClauseTest extends MissingClassesTestBase {

  private static final MissingDefinitionContext referencedFrom =
      MissingDefinitionClassContext.builder()
          .setClassContext(Reference.classFromClass(Main.class))
          .setOrigin(getOrigin(Main.class))
          .build();

  public MissingClassReferencedFromImplementsClauseTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  ClassReference getMissingClassReference() {
    return Reference.classFromClass(MissingInterface.class);
  }

  // The tests explicitly disable desugaring to prevent desugaring warnings.
  // TODO(b/179341237): Consider if desugaring warnings should be D8 only, since in a way they are
  //  all duplicates of the MissingClassesDiagnostic.
  @Test(expected = CompilationFailedException.class)
  public void testNoRules() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom),
        TestCompilerBuilder::disableDesugaring);
  }

  @Test
  public void testDontWarnMainClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addDontWarn(Main.class).andThen(TestCompilerBuilder::disableDesugaring));
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addDontWarn(MissingInterface.class).andThen(TestCompilerBuilder::disableDesugaring));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom),
        addIgnoreWarnings().andThen(TestCompilerBuilder::disableDesugaring));
  }

  static class Main implements MissingInterface {

    public static void main(String[] args) {}
  }
}
