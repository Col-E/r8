// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionMethodContextImpl;
import com.android.tools.r8.utils.MethodReferenceUtils;
import org.junit.Test;

public class MissingClassReferencedFromCatchHandlerTest extends MissingClassesTestBase {

  private static final DefinitionContext referencedFrom =
      DefinitionMethodContextImpl.builder()
          .setMethodContext(MethodReferenceUtils.mainMethod(Main.class))
          .setOrigin(getOrigin(Main.class))
          .build();

  public MissingClassReferencedFromCatchHandlerTest(TestParameters parameters) {
    super(parameters);
  }

  @Test(expected = CompilationFailedException.class)
  public void testNoRules() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom),
        this::configure);
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
        diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom),
        addIgnoreWarnings().andThen(this::configure));
  }

  public void configure(R8FullTestBuilder testBuilder) {
    testBuilder.addOptionsModification(
        options -> options.getCfCodeAnalysisOptions().setEnableUnverifiableCodeReporting(false));
  }

  static class Main {

    public static void main(String[] args) {
      try {
        nop();
      } catch (MissingClass ignore) {
      }
    }

    private static void nop() {}
  }
}
