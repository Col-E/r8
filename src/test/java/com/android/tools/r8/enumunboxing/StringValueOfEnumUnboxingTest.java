// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringValueOfEnumUnboxingTest extends EnumUnboxingTestBase {
  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public StringValueOfEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = Main.class;
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addProgramClasses(classToTest, MyEnum.class)
            .addKeepMainRule(classToTest)
            .addKeepRules(enumKeepRules.getKeepRules())
            .enableNeverClassInliningAnnotations()
            .enableInliningAnnotations()
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspectDiagnosticMessages(
                m -> assertEnumIsUnboxed(MyEnum.class, classToTest.getSimpleName(), m))
            .run(parameters.getRuntime(), classToTest)
            .assertSuccess();
    assertLines2By2Correct(run.getStdOut());
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B,
    C
  }

  static class Main {
    public static void main(String[] args) {
      System.out.println(MyEnum.A.ordinal());
      System.out.println(0);
      System.out.println(getString(MyEnum.A));
      System.out.println("A");
      System.out.println(getString(null));
      System.out.println("null");
    }

    @NeverInline
    private static String getString(MyEnum e) {
      return String.valueOf(e);
    }
  }
}
