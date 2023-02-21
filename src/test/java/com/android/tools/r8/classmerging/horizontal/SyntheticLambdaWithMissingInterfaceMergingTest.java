// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SyntheticLambdaWithMissingInterfaceMergingTest extends TestBase {

  @Parameter(0)
  public boolean enableOptimization;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, optimize: {0}")
  public static List<Object[]> parameters() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, I.class)
        .addDontWarn(J.class)
        .addKeepClassAndMembersRules(Main.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .applyIf(!enableOptimization, TestShrinkerBuilder::addDontOptimize)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    allOf(
                        diagnosticType(UnverifiableCfCodeDiagnostic.class),
                        diagnosticMessage(
                            containsString(
                                "Unverifiable code in `void "
                                    + Main.class.getTypeName()
                                    + ".dead()`")))))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I");
  }

  static class Main {
    public static void main(String[] args) {
      I i = () -> System.out.println("I");
      i.m1();
    }

    static void dead() {
      J j = () -> System.out.println("J");
      j.m2();
    }
  }

  interface I {
    void m1();
  }

  interface J {
    void m2();
  }
}
