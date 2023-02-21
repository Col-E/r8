// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.checkdiscarded;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilationIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckSubclassDiscardedEntirelyTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean shrink;

  @Parameter(2)
  public boolean wildcard;

  @Parameters(name = "{0}, shrink: {1}, wildcard: {2}")
  public static List<Object[]> parameters() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    assertFailsCompilationIf(
        !shrink,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addKeepRules(
                    "-assumenosideeffects class " + Main.class.getTypeName() + " {",
                    "  static boolean shrink() return " + shrink + ";",
                    "}",
                    "-checkdiscard class "
                        + Secret.class.getTypeName()
                        + (wildcard ? " { *; }" : ""),
                    // Disable optimizations for items hit by the -checkdiscard rule. Note that
                    // the evaluation of -checkdiscard rules does not recurse into super classes,
                    // thus
                    // to match only the items hit by the -checkdiscard rule we use an -if rule that
                    // matches the methods on Secret. Specifically, this rule should not keep the
                    // method Public.printPublicAllowInlining().
                    "-if class " + Secret.class.getTypeName() + " { *** *(...); }",
                    "-keep,allowshrinking class " + Secret.class.getTypeName() + " {",
                    "   <1> <2>(...);",
                    "}")
                .addVerticallyMergedClassesInspector(
                    VerticallyMergedClassesInspector::assertNoClassesMerged)
                .enableInliningAnnotations()
                .enableNoVerticalClassMergingAnnotations()
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      if (shrink) {
                        diagnostics.assertNoMessages();
                      } else {
                        diagnostics.assertAllErrorsMatch(
                            diagnosticType(CheckDiscardDiagnostic.class));
                      }
                    })
                .inspect(
                    inspector -> {
                      // We only get here if the branch in Main.main() is pruned, or the
                      // -checkdiscard
                      // rule should have failed.
                      assertTrue(shrink);

                      ClassSubject mainClassSubject = inspector.clazz(Main.class);
                      assertThat(mainClassSubject, isPresent());
                      assertTrue(
                          mainClassSubject
                              .mainMethod()
                              .streamInstructions()
                              .anyMatch(instruction -> instruction.isConstString("Public 2")));

                      ClassSubject publicClassSubject = inspector.clazz(Public.class);
                      assertThat(publicClassSubject, isPresent());
                      assertThat(
                          publicClassSubject.uniqueMethodWithOriginalName("printPublic"),
                          isPresent());
                      assertThat(
                          publicClassSubject.uniqueMethodWithOriginalName(
                              "printPublicAllowInlining"),
                          isAbsent());

                      assertThat(inspector.clazz(Secret.class), isAbsent());
                    })
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithOutputLines("Public 1", "Public 2"));
  }

  static class Main {

    public static void main(String[] args) {
      Public.printPublic();
      Public.printPublicAllowInlining();
      if (!shrink()) {
        Secret.printSecret();
      }
    }

    static boolean shrink() {
      throw new RuntimeException();
    }
  }

  @NoVerticalClassMerging
  static class Public {

    @NeverInline
    static void printPublic() {
      System.out.println("Public 1");
    }

    static void printPublicAllowInlining() {
      System.out.println("Public 2");
    }
  }

  static class Secret extends Public {

    static void printSecret() {
      System.out.println("Secret");
    }
  }
}
