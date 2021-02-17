// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.diagnostic.MissingDefinitionContext;
import com.android.tools.r8.diagnostic.internal.MissingDefinitionMethodContext;
import com.android.tools.r8.missingclasses.MissingClassReferencedFromNestMemberAttributeTest.Main;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Collection;
import org.junit.Test;

/**
 * If a method reference that refers to a missing class does not resolve to a definition, then the
 * enclosing method is to be blamed.
 */
public class MissingClassReferencedFromInvokeVirtualToAbsentMethodParameterTest
    extends MissingClassesTestBase {

  private static final MissingDefinitionContext referencedFrom =
      MissingDefinitionMethodContext.builder()
          .setMethodContext(MethodReferenceUtils.mainMethod(Main.class))
          .setOrigin(getOrigin(Main.class))
          .build();

  public MissingClassReferencedFromInvokeVirtualToAbsentMethodParameterTest(
      TestParameters parameters) {
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
        addMain().andThen(addDontWarn(MissingClass.class)),
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

  static Collection<byte[]> getProgramClassFileData() throws IOException {
    return ImmutableList.of(transformer(Main.class).removeMethodsWithName("get").transform());
  }

  static class Main {

    public static void main(String[] args) {
      new Main().get(null);
    }

    /** Removed by transformer. */
    public void get(MissingClass mc) {}
  }
}
