// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionClassContextImpl;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Collection;
import org.junit.Test;

public class MissingClassReferencedFromNestMemberAttributeTest extends MissingClassesTestBase {

  private static final DefinitionContext referencedFrom =
      DefinitionClassContextImpl.builder()
          .setClassContext(Reference.classFromClass(Main.class))
          .setOrigin(getOrigin(Main.class))
          .build();

  public MissingClassReferencedFromNestMemberAttributeTest(TestParameters parameters) {
    super(parameters);
  }

  @Test(expected = CompilationFailedException.class)
  public void testNoRules() throws Exception {
    compileWithExpectedDiagnostics(
        addMain(), diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom));
  }

  @Test
  public void testDontWarnMainClass() throws Exception {
    compileWithExpectedDiagnostics(
        addMain().andThen(addDontWarn(Main.class)), TestDiagnosticMessages::assertNoMessages);
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        addMain().andThen(addDontWarn(Main.MissingClass.class)),
        TestDiagnosticMessages::assertNoMessages);
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        addMain().andThen(addIgnoreWarnings()),
        diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom));
  }

  private ThrowableConsumer<R8FullTestBuilder> addMain() {
    return builder ->
        builder.addProgramClassFileData(getProgramClassFileData()).addKeepMainRule(Main.class);
  }

  @Override
  ClassReference getMissingClassReference() {
    return Reference.classFromClass(Main.MissingClass.class);
  }

  static Collection<byte[]> getProgramClassFileData() throws IOException {
    return ImmutableList.of(
        transformer(Main.class).setNest(Main.class, Main.MissingClass.class).transform());
  }

  static /*host*/ class Main {

    static /*member*/ class MissingClass {}

    public static void main(String[] args) {}
  }
}
