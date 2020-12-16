// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.shaking.MissingClassesDiagnostic;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import org.junit.Test;

public class MissingClassReferencedFromNewInstanceTest extends MissingClassesTestBase {

  public MissingClassReferencedFromNewInstanceTest(
      DontWarnConfiguration dontWarnConfiguration, TestParameters parameters) {
    super(dontWarnConfiguration, parameters);
  }

  @Test
  public void test() throws Exception {
    AssertUtils.assertFailsCompilationIf(
        // TODO(b/175542052): Should not fail compilation with -dontwarn Main.
        !getDontWarnConfiguration().isDontWarnMissingClass(),
        () ->
            compileWithExpectedDiagnostics(
                Main.class, MissingClass.class, this::inspectDiagnostics));
  }

  private void inspectDiagnostics(TestDiagnosticMessages diagnostics) {
    // TODO(b/175542052): Should also not have any diagnostics with -dontwarn Main.
    if (getDontWarnConfiguration().isDontWarnMissingClass()) {
      diagnostics.assertNoMessages();
      return;
    }

    diagnostics
        .assertOnlyErrors()
        .assertErrorsCount(1)
        .assertAllErrorsMatch(diagnosticType(MissingClassesDiagnostic.class));

    MissingClassesDiagnostic diagnostic = (MissingClassesDiagnostic) diagnostics.getErrors().get(0);
    assertEquals(
        !getDontWarnConfiguration().isDontWarnMissingClass(),
        diagnostic.getMissingClasses().stream()
            .map(TypeReference::getTypeName)
            .anyMatch(MissingClass.class.getTypeName()::equals));
  }

  static class Main {

    public static void main(String[] args) {
      new MissingClass();
    }
  }

  static class MissingClass {}
}
