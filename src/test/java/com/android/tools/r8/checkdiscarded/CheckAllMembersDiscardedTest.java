// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.checkdiscarded;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.AssertUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckAllMembersDiscardedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean shrinkMethodReferences;

  @Parameter(2)
  public boolean shrinkTypeReference;

  @Parameters(name = "{0}, shrink method references: {1}, shrink type reference: {2}")
  public static List<Object[]> parameters() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    AssertUtils.assertFailsCompilationIf(
        !shrinkMethodReferences || !shrinkTypeReference,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addKeepRules(
                    "-assumenosideeffects class " + Main.class.getTypeName() + " {",
                    "  static boolean shrinkMethodReferences() return "
                        + shrinkMethodReferences
                        + ";",
                    "  static boolean shrinkTypeReference() return " + shrinkTypeReference + ";",
                    "}",
                    // When all members on a class are marked by -checkdiscard, we interpret it as
                    // if the class should also be fully discarded.
                    getRuleForSecret("checkdiscard"),
                    getRuleForSecret("keep,allowshrinking"))
                .setMinApi(parameters)
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      if (shrinkMethodReferences && shrinkTypeReference) {
                        diagnostics.assertNoMessages();
                      } else {
                        diagnostics.assertErrorsMatch(diagnosticType(CheckDiscardDiagnostic.class));
                      }
                    })
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithEmptyOutput());
  }

  private static String getRuleForSecret(String directive) {
    return StringUtils.joinLines(
        "-" + directive + " class " + Secret.class.getTypeName() + " {",
        "  void <init>();",
        "  static void foo();",
        "  static void bar();",
        "}");
  }

  static class Main {

    public static void main(String[] args) {
      if (!shrinkMethodReferences()) {
        Secret.foo();
        Secret.bar();
      }
      if (!shrinkTypeReference()) {
        System.out.println(Secret.class);
      }
    }

    static boolean shrinkMethodReferences() {
      throw new RuntimeException();
    }

    static boolean shrinkTypeReference() {
      throw new RuntimeException();
    }
  }

  static class Secret {

    static void foo() {
      System.out.println("Secret Foo");
    }

    static void bar() {
      System.out.println("Secret Bar");
    }
  }
}
