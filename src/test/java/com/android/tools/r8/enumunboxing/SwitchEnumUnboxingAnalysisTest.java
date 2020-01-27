// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SwitchEnumUnboxingAnalysisTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return enumUnboxingTestParameters();
  }

  public SwitchEnumUnboxingAnalysisTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static final Class<?> ENUM_CLASS = MyEnum.class;

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<Switch> classToTest = Switch.class;
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addInnerClasses(SwitchEnumUnboxingAnalysisTest.class)
            .addKeepMainRule(classToTest)
            .addKeepRules(KEEP_ENUM)
            .enableInliningAnnotations()
            .addOptionsModification(this::enableEnumOptions)
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspectDiagnosticMessages(
                m -> assertEnumIsBoxed(ENUM_CLASS, classToTest.getSimpleName(), m))
            .run(parameters.getRuntime(), classToTest)
            .assertSuccess();
    assertLines2By2Correct(run.getStdOut());
  }

  enum MyEnum {
    A,
    B,
    C
  }

  static class Switch {

    public static void main(String[] args) {
      System.out.println(switchOnEnum(MyEnum.A));
      System.out.println(0xC0FFEE);
      System.out.println(switchOnEnum(MyEnum.B));
      System.out.println(0xBABE);
    }

    // Avoid removing the switch entirely.
    @NeverInline
    static int switchOnEnum(MyEnum e) {
      switch (e) {
        case A:
          return 0xC0FFEE;
        case B:
          return 0xBABE;
        default:
          return 0xDEADBEEF;
      }
    }
  }
}
