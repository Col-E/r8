// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.checkdiscarded;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilationIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckClassDiscardedEntirelyTest extends TestBase {

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
                    getRuleForSecret("checkdiscard", wildcard),
                    // The following call intentionally passes 'true' instead of 'wildcard'. Indeed,
                    // the -checkdiscard rule for a class implicitly expands to a -checkdiscard rule
                    // for the class and all of its members. Therefore, the corresponding rule to
                    // disallow optimizations must apply to all members.
                    getRuleForSecret("keep,allowshrinking", true))
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
                      // -checkdiscard rule should have failed.
                      assertTrue(shrink);
                      assertThat(inspector.clazz(Secret.class), isAbsent());
                    })
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithEmptyOutput());
  }

  private static String getRuleForSecret(String directive, boolean wildcard) {
    return "-" + directive + " class " + Secret.class.getTypeName() + (wildcard ? " { *; }" : "");
  }

  static class Main {

    public static void main(String[] args) {
      if (!shrink()) {
        System.out.println(Secret.get());
      }
    }

    static boolean shrink() {
      throw new RuntimeException();
    }
  }

  static class Secret {

    static String get() {
      return "Secret";
    }
  }
}
