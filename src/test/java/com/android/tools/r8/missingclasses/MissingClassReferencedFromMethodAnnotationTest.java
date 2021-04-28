// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionMethodContextImpl;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import org.junit.Test;

// TODO(b/179456539): This test should fail without -keepattributes RuntimeVisibleAnnotations, but
//  we retain missing annotations even if there is no -keepattributes *Annotations*.
public class MissingClassReferencedFromMethodAnnotationTest extends MissingClassesTestBase {

  private static final DefinitionContext referencedFrom =
      DefinitionMethodContextImpl.builder()
          .setMethodContext(MethodReferenceUtils.mainMethod(Main.class))
          .setOrigin(getOrigin(Main.class))
          .build();

  public MissingClassReferencedFromMethodAnnotationTest(TestParameters parameters) {
    super(parameters);
  }

  @Test(expected = CompilationFailedException.class)
  public void testNoRules() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class, diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom));
  }

  @Test
  public void testDontWarnMainClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class, TestDiagnosticMessages::assertNoMessages, addDontWarn(Main.class));
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addDontWarn(MissingRuntimeAnnotation.class));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom),
        addIgnoreWarnings());
  }

  @Override
  ClassReference getMissingClassReference() {
    return Reference.classFromClass(MissingRuntimeAnnotation.class);
  }

  static class Main {

    @MissingRuntimeAnnotation
    public static void main(String[] args) {}
  }
}
