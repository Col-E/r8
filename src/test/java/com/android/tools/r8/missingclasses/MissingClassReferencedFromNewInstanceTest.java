// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.MissingClassesDiagnostic;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class MissingClassReferencedFromNewInstanceTest extends MissingClassesTestBase {

  public MissingClassReferencedFromNewInstanceTest(
      DontWarnConfiguration dontWarnConfiguration, TestParameters parameters) {
    super(dontWarnConfiguration, parameters);
  }

  @Test
  public void test() throws Exception {
    AssertUtils.assertFailsCompilationIf(
        getDontWarnConfiguration().isNone(),
        () ->
            compileWithExpectedDiagnostics(
                Main.class, MissingClass.class, this::inspectDiagnostics));
  }

  private void inspectDiagnostics(TestDiagnosticMessages diagnostics) {
    // There should be no diagnostics with -dontwarn.
    if (getDontWarnConfiguration().isDontWarn()) {
      diagnostics.assertNoMessages();
      return;
    }

    // There should be a single warning if we are compiling with -ignorewarnings. Otherwise, there
    // should be a single error.
    MissingClassesDiagnostic diagnostic;
    if (getDontWarnConfiguration().isIgnoreWarnings()) {
      diagnostics
          .assertOnlyWarnings()
          .assertWarningsCount(1)
          .assertAllWarningsMatch(diagnosticType(MissingClassesDiagnostic.class));
      diagnostic = (MissingClassesDiagnostic) diagnostics.getWarnings().get(0);
    } else {
      diagnostics
          .assertOnlyErrors()
          .assertErrorsCount(1)
          .assertAllErrorsMatch(diagnosticType(MissingClassesDiagnostic.class));
      diagnostic = (MissingClassesDiagnostic) diagnostics.getErrors().get(0);
    }

    // Inspect the diagnostic.
    assertEquals(
        ImmutableSet.of(Reference.classFromClass(MissingClass.class)),
        diagnostic.getMissingClasses());
    assertEquals(getExpectedDiagnosticMessage(), diagnostic.getDiagnosticMessage());
  }

  private String getExpectedDiagnosticMessage() {
    ClassReference missingClass = Reference.classFromClass(MissingClass.class);
    MethodReference context;
    try {
      context = Reference.methodFromMethod(Main.class.getDeclaredMethod("main", String[].class));
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
    String referencedFromSuffix =
        " (referenced from: " + MethodReferenceUtils.toSourceString(context) + ")";
    if (getDontWarnConfiguration().isIgnoreWarnings()) {
      return "Missing class " + missingClass.getTypeName() + referencedFromSuffix;
    }
    return "Compilation can't be completed because the following class is missing: "
        + missingClass.getTypeName()
        + referencedFromSuffix
        + ".";
  }

  static class Main {

    public static void main(String[] args) {
      new MissingClass();
    }
  }
}
