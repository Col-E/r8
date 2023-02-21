// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoHorizontalClassMerging;
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
public class MainDexListInliningTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsEndingAtExcluding(apiLevelWithNativeMultiDexSupport())
        .build();
  }

  public MainDexListInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  // TODO(b/181858113): This test should be converted to a main-dex-rules test.
  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addMainDexListClasses(Main.class)
            .collectMainDexClasses()
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters)
            .allowDiagnosticMessages()
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics
                        .assertOnlyWarnings()
                        .assertWarningsMatch(
                            diagnosticType(UnsupportedMainDexListUsageDiagnostic.class)));

    CodeInspector inspector = compileResult.inspector();

    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());

    // A is not allowed to be inlined and is therefore present.
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    // B should be referenced from Main.main.
    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    compileResult.inspectMainDexClasses(
        mainDexClasses -> {
          assertTrue(mainDexClasses.contains(mainClassSubject.getFinalName()));
          // Since we passed a main-dex list the traced references A and B are not automagically
          // included.
          assertFalse(mainDexClasses.contains(aClassSubject.getFinalName()));
          assertFalse(mainDexClasses.contains(bClassSubject.getFinalName()));
        });
  }

  static class Main {

    public static void main(String[] args) {
      // Should be inlined.
      A.m();
    }
  }

  @NoHorizontalClassMerging
  static class A {

    public static void m() {
      System.out.println(B.class);
    }
  }

  @NoHorizontalClassMerging
  static class B {}
}
