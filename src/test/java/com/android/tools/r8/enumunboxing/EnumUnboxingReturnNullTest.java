// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumUnboxingReturnNullTest extends EnumUnboxingTestBase {

  private static final Class<MyEnum> ENUM_CLASS = MyEnum.class;
  private static final String EXPECTED_RESULT =
      StringUtils.lines(
          "print1", "true", "print2", "true", "print2", "false", "0", "print3", "true");

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameterized.Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumUnboxingReturnNullTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = ReturnNull.class;
    testForR8(parameters.getBackend())
        .addProgramClasses(classToTest, ENUM_CLASS)
        .addKeepMainRule(classToTest)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(ENUM_CLASS))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), classToTest)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B,
    C
  }

  static class ReturnNull {

    public static void main(String[] args) {
      MyEnum myEnum1 = printAndReturnNull();
      System.out.println(myEnum1 == null);
      MyEnum myEnum2 = printAndReturnMaybeNull(true);
      System.out.println(myEnum2 == null);
      MyEnum myEnum3 = printAndReturnMaybeNull(false);
      System.out.println(myEnum3 == null);
      System.out.println(MyEnum.A.ordinal());
      MyEnum[] myEnums = printAndReturnNullArray();
      System.out.println(myEnums == null);
    }

    @NeverInline
    static MyEnum printAndReturnNull() {
      System.out.println("print1");
      return null;
    }

    @NeverInline
    static MyEnum printAndReturnMaybeNull(boolean bool) {
      System.out.println("print2");
      if (bool) {
        return null;
      } else {
        return MyEnum.B;
      }
    }

    @NeverInline
    static MyEnum[] printAndReturnNullArray() {
      System.out.println("print3");
      return null;
    }
  }
}
