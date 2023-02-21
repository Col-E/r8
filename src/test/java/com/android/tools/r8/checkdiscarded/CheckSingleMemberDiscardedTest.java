// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.checkdiscarded;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
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
public class CheckSingleMemberDiscardedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean shrink;

  @Parameters(name = "{0}, shrink: {1}")
  public static List<Object[]> parameters() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void test() throws Exception {
    AssertUtils.assertFailsCompilationIf(
        !shrink,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(getClass())
                .addKeepMainRule(Main.class)
                .addKeepRules(
                    "-assumenosideeffects class " + Main.class.getTypeName() + " {",
                    "  static boolean shrink() return " + shrink + ";",
                    "}",
                    getRuleForSecret("checkdiscard"),
                    getRuleForSecret("keep,allowshrinking"))
                .enableInliningAnnotations()
                .setMinApi(parameters)
                .compile()
                .run(parameters.getRuntime(), Main.class)
                .assertSuccessWithOutputLines("Public"));
  }

  private static String getRuleForSecret(String directive) {
    return StringUtils.joinLines(
        "-" + directive + " class " + Main.class.getTypeName() + " {",
        "  static void printSecret();",
        "}");
  }

  static class Main {

    public static void main(String[] args) {
      printPublic();
      if (!shrink()) {
        printSecret();
      }
    }

    @NeverInline
    static void printPublic() {
      System.out.println("Public");
    }

    static void printSecret() {
      System.out.println("Secret");
    }

    static boolean shrink() {
      throw new RuntimeException();
    }
  }
}
