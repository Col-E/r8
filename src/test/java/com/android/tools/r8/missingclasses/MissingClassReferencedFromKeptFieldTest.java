// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import org.junit.Test;

/** If a field definition refers to a missing class, then the field definition is to be blamed. */
public class MissingClassReferencedFromKeptFieldTest extends MissingClassesTestBase {

  private static final FieldReference referencedFrom =
      Reference.field(
          Reference.classFromClass(Main.class),
          "FIELD",
          Reference.classFromClass(MissingClass.class));

  public MissingClassReferencedFromKeptFieldTest(TestParameters parameters) {
    super(parameters);
  }

  @Test(expected = CompilationFailedException.class)
  public void testNoRules() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithNoRules(diagnostics, referencedFrom),
        this::addKeepFieldRule);
  }

  @Test
  public void testDontWarnMainClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addDontWarn(Main.class).andThen(this::addKeepFieldRule));
  }

  @Test
  public void testDontWarnMissingClass() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        TestDiagnosticMessages::assertNoMessages,
        addDontWarn(MissingClass.class).andThen(this::addKeepFieldRule));
  }

  @Test
  public void testIgnoreWarnings() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> inspectDiagnosticsWithIgnoreWarnings(diagnostics, referencedFrom),
        addIgnoreWarnings().andThen(this::addKeepFieldRule));
  }

  private void addKeepFieldRule(R8FullTestBuilder builder) {
    builder.addKeepRules(
        "-keep class " + Main.class.getTypeName() + " {",
        "  public static " + MissingClass.class.getTypeName() + " FIELD;",
        "}");
  }

  static class Main {

    public static MissingClass FIELD;

    public static void main(String[] args) {}
  }
}
