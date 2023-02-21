// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;


import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public LambdaEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0", "0", "1", "0", "0");
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
      boolean[] booleans = new boolean[] {true, false};
      forEach(booleans, Main::printAndGetEnum);
      System.out.println(printAndGetEnum(true).ordinal());
    }

    @NeverInline
    private static MyEnum printAndGetEnum(boolean b) {
      MyEnum myEnum = b ? MyEnum.A : MyEnum.B;
      System.out.println(myEnum.ordinal());
      return myEnum;
    }

    @NeverInline
    static void forEach(boolean[] booleans, MyBooleanConsumer consumer) {
      for (boolean b : booleans) {
        consumer.accept(b);
      }
    }
  }

  interface MyBooleanConsumer {

    void accept(Boolean b);
  }
}
