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
public class PhiEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public PhiEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addProgramClasses(Phi.class, MyEnum.class)
            .addKeepMainRule(Phi.class)
            .addKeepRules(enumKeepRules.getKeepRules())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspectDiagnosticMessages(
                m -> assertEnumIsUnboxed(MyEnum.class, Phi.class.getSimpleName(), m))
            .run(parameters.getRuntime(), Phi.class)
            .assertSuccess();
    assertLines2By2Correct(run.getStdOut());
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B,
    C
  }

  static class Phi {

    public static void main(String[] args) {
      nonNullTest();
      nullTest();
      arrayGetAndPhiTest();
      argumentsAndPhiTest();
    }

    private static void nonNullTest() {
      System.out.println(switchOn(1).ordinal());
      System.out.println(1);
      System.out.println(switchOn(2).ordinal());
      System.out.println(2);
    }

    private static void nullTest() {
      System.out.println(switchOnWithNull(1).ordinal());
      System.out.println(1);
      System.out.println(switchOnWithNull(2) == null);
      System.out.println(true);
    }

    private static void arrayGetAndPhiTest() {
      MyEnum[] values = MyEnum.values();
      System.out.println(arrayGetAndPhi(values, true));
      System.out.println(values[1].ordinal());
      System.out.println(arrayGetAndPhi(values, false));
      System.out.println(values[0].ordinal());
    }

    private static void argumentsAndPhiTest() {
      System.out.println(argumentsAndPhi(MyEnum.A, MyEnum.B, true));
      System.out.println(MyEnum.B.ordinal());
      System.out.println(argumentsAndPhi(MyEnum.A, MyEnum.B, false));
      System.out.println(MyEnum.A.ordinal());
    }

    @NeverInline
    static MyEnum switchOn(int i) {
      MyEnum returnValue;
      switch (i) {
        case 0:
          returnValue = MyEnum.A;
          break;
        case 1:
          returnValue = MyEnum.B;
          break;
        default:
          returnValue = MyEnum.C;
      }
      return returnValue;
    }

    @NeverInline
    static MyEnum switchOnWithNull(int i) {
      MyEnum returnValue;
      switch (i) {
        case 0:
          returnValue = MyEnum.A;
          break;
        case 1:
          returnValue = MyEnum.B;
          break;
        default:
          returnValue = null;
      }
      return returnValue;
    }

    @NeverInline
    static int arrayGetAndPhi(MyEnum[] enums, boolean b) {
      return (b ? enums[1] : enums[0]).ordinal();
    }

    @NeverInline
    static int argumentsAndPhi(MyEnum e0, MyEnum e1, boolean b) {
      return (b ? e1 : e0).ordinal();
    }
  }
}
