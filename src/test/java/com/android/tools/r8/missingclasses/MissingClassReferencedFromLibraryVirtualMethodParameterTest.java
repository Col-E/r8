// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionMethodContextImpl;
import com.android.tools.r8.utils.MethodReferenceUtils;
import org.junit.Test;

public class MissingClassReferencedFromLibraryVirtualMethodParameterTest
    extends MissingClassesTestBase {

  private static final DefinitionContext referencedFrom =
      DefinitionMethodContextImpl.builder()
          .setMethodContext(
              MethodReferenceUtils.methodFromMethod(Library.class, "method", MissingClass.class))
          .setOrigin(getOrigin(Library.class))
          .build();

  public MissingClassReferencedFromLibraryVirtualMethodParameterTest(TestParameters parameters) {
    super(parameters);
  }

  @Test(expected = CompilationFailedException.class)
  public void testNoRules() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom),
        addLibrary());
  }

  /**
   * Main is the closest program context to the missing class reference, but it is not the context
   * of the missing class reference (Library is). Therefore, compilation still fails in this case.
   */
  @Test(expected = CompilationFailedException.class)
  public void testDontWarnMainClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom),
        addLibrary().andThen(addDontWarn(Main.class)));
  }

  @Test
  public void testDontWarnLibraryClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addLibrary().andThen(addDontWarn(Library.class)));
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addLibrary().andThen(addDontWarn(MissingClass.class)));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom),
        addLibrary().andThen(addIgnoreWarnings()));
  }

  private ThrowableConsumer<R8FullTestBuilder> addLibrary() {
    return builder ->
        builder.addLibraryClasses(Library.class).addLibraryFiles(getMostRecentAndroidJar());
  }

  static class Main extends Library {

    public static void main(String[] args) {}
  }

  public static class Library {

    public void method(MissingClass mc) {}
  }
}
