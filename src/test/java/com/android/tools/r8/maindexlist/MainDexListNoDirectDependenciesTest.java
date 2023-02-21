// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.UnsupportedMainDexListUsageDiagnostic;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MainDexListNoDirectDependenciesTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  public MainDexListNoDirectDependenciesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  // TODO(b/181858113): This test is likely obsolete once main-dex-list support is removed.
  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addMainDexListClasses(A.class)
            .addMainDexKeepClassRules(B.class)
            .collectMainDexClasses()
            .noTreeShaking()
            .setMinApi(parameters)
            .allowDiagnosticMessages()
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics
                        .assertOnlyWarnings()
                        .assertWarningsMatch(
                            diagnosticType(UnsupportedMainDexListUsageDiagnostic.class)));

    CodeInspector inspector = compileResult.inspector();

    ClassSubject aClassSubject = inspector.clazz(A.class);
    ClassSubject referencedFromAClassSubject = inspector.clazz(ReferencedFromA.class);
    ClassSubject bClassSubject = inspector.clazz(B.class);
    ClassSubject referencedFromBClassSubject = inspector.clazz(ReferencedFromB.class);

    compileResult.inspectMainDexClasses(
        mainDexClasses -> {
          assertTrue(mainDexClasses.contains(aClassSubject.getFinalName()));
          // It is assumed that the provided main dex list includes its direct dependencies.
          // Therefore, we explicitly do not include the direct dependencies of the main dex list
          // classes in the final main dex, since this would lead to the dependencies of the
          // dependencies being included in the main dex.
          assertFalse(mainDexClasses.contains(referencedFromAClassSubject.getFinalName()));
          assertTrue(mainDexClasses.contains(bClassSubject.getFinalName()));
          assertTrue(mainDexClasses.contains(referencedFromBClassSubject.getFinalName()));
        });
  }

  static class A {

    public void m() {
      System.out.println(ReferencedFromA.class);
    }
  }

  static class ReferencedFromA {}

  static class B {

    public void m() {
      System.out.println(ReferencedFromB.class);
    }
  }

  static class ReferencedFromB {}
}
