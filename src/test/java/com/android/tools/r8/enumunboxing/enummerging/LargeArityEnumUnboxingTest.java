// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing.enummerging;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LargeArityEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public LargeArityEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(LargeArityEnumUnboxingTest.class)
        .addKeepMainRule(LargeArityEnum.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(LargeArityEnum.MyEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), LargeArityEnum.class)
        .assertSuccessWithOutputLines("171.0", "189.0", "271.0", "299.0");
  }

  static class LargeArityEnum {

    @NeverClassInline
    enum MyEnum {
      A(1) {
        @NeverInline
        public double foo(
            long op1,
            long op2,
            double op3,
            double op4,
            int op5,
            int op6,
            long op7,
            long op8,
            double op9,
            double op10,
            int op11,
            int op12,
            long op13,
            long op14,
            double op15,
            double op16,
            int op17,
            int op18) {
          return mul * op1 + op2 + op3 + op4 + op5 + op6 + op7 + op8 + op9 + op10 + op11 + op12
              + op13 + op14 + op15 + op16 + op17 + op18;
        }
      },
      B(3) {
        @NeverInline
        public double foo(
            long op1,
            long op2,
            double op3,
            double op4,
            int op5,
            int op6,
            long op7,
            long op8,
            double op9,
            double op10,
            int op11,
            int op12,
            long op13,
            long op14,
            double op15,
            double op16,
            int op17,
            int op18) {
          return mul * mul * op1
              + op2
              + op3
              + op4
              + op5
              + op6
              + op7
              + op8
              + op9
              + op10
              + op11
              + op12
              + op13
              + op14
              + op15
              + op16
              + op17
              + op18 * mul;
        }
      };

      final int mul;

      MyEnum(int mul) {
        this.mul = mul;
      }

      // Method with large arity.
      public abstract double foo(
          long op1,
          long op2,
          double op3,
          double op4,
          int op5,
          int op6,
          long op7,
          long op8,
          double op9,
          double op10,
          int op11,
          int op12,
          long op13,
          long op14,
          double op15,
          double op16,
          int op17,
          int op18);
    }

    @SuppressWarnings("ConstantConditions")
    public static void main(String[] args) {
      System.out.println(
          MyEnum.A.foo(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18));
      System.out.println(
          MyEnum.A.foo(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19));
      System.out.println(
          MyEnum.B.foo(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20));
      System.out.println(
          MyEnum.B.foo(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21));
    }
  }
}
