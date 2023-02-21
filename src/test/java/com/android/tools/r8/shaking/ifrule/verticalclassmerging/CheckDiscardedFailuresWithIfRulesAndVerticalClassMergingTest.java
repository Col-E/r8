// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckDiscardedFailuresWithIfRulesAndVerticalClassMergingTest extends TestBase {

  @Parameter(0)
  public boolean enableCheckDiscard;

  @Parameter(1)
  public boolean enableVerticalClassMerging;

  @Parameter(2)
  public TestParameters parameters;

  @Parameters(name = "{2}, check discard: {0}, vertical class merging: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    AssertUtils.assertFailsCompilationIf(
        enableCheckDiscard,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addKeepRules(
                    "-if class " + A.class.getTypeName(), "-keep class " + C.class.getTypeName())
                .addNoVerticalClassMergingAnnotations()
                .addVerticallyMergedClassesInspector(
                    inspector -> {
                      if (enableVerticalClassMerging) {
                        inspector.assertMergedIntoSubtype(A.class);
                      } else {
                        inspector.assertNoClassesMerged();
                      }
                    })
                // Intentionally fail compilation due to -checkdiscard. This triggers the
                // (re)running of the Enqueuer after the final round of tree shaking, for generating
                // -whyareyoukeeping output.
                .applyIf(
                    enableCheckDiscard,
                    testBuilder ->
                        testBuilder.addKeepRules("-checkdiscard class " + Main.class.getTypeName()))
                .applyIf(
                    !enableVerticalClassMerging,
                    R8TestBuilder::enableNoVerticalClassMergingAnnotations)
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      if (enableCheckDiscard) {
                        diagnostics.assertErrorsMatch(diagnosticType(CheckDiscardDiagnostic.class));
                      } else {
                        diagnostics.assertNoMessages();
                      }
                    })
                // TODO(b/266049507): It is questionable not to keep C when vertical class merging
                // is enabled. A simple fix is to disable vertical class merging of classes matched
                // by the -if condition.
                .inspect(
                    inspector ->
                        assertThat(
                            inspector.clazz(C.class),
                            notIf(isPresent(), enableVerticalClassMerging)))
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithOutputLines("B"));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new B());
    }
  }

  @NoVerticalClassMerging
  static class A {}

  static class B extends A {

    @Override
    public String toString() {
      return "B";
    }
  }

  static class C {}
}
