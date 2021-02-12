// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;

public class MissingClassReferencedFromClassAnnotationWithDataTest extends MissingClassesTestBase {

  private static final ClassReference referencedFrom = Reference.classFromClass(Main.class);

  public MissingClassReferencedFromClassAnnotationWithDataTest(TestParameters parameters) {
    super(parameters);
  }

  @Test(expected = CompilationFailedException.class)
  public void testNoRules() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom),
        addRuntimeAnnotation());
  }

  @Test
  public void testDontWarnMainClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addRuntimeAnnotation().andThen(addDontWarn(Main.class)));
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addRuntimeAnnotation().andThen(addDontWarn(MissingClass.class)));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom),
        addRuntimeAnnotation().andThen(addIgnoreWarnings()));
  }

  private ThrowableConsumer<R8FullTestBuilder> addRuntimeAnnotation() {
    return builder ->
        builder
            .addProgramClasses(RuntimeAnnotation.class)
            .addKeepClassRules(RuntimeAnnotation.class)
            .addKeepRuntimeVisibleAnnotations();
  }

  @RuntimeAnnotation(data = MissingClass.class)
  static class Main {

    public static void main(String[] args) {}
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface RuntimeAnnotation {
    Class<?> data();
  }
}
