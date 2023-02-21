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
public class NullOutValueInvokeEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final Class<MyEnum> ENUM_CLASS = MyEnum.class;

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public NullOutValueInvokeEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = NullOutValueInvoke.class;
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
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B,
    C
  }

  static class NullOutValueInvoke {

    public static void main(String[] args) {
      printAndGetMyEnum();
      printAndGetMyEnum();
    }

    @SuppressWarnings("UnusedReturnValue")
    @NeverInline
    static MyEnum printAndGetMyEnum() {
      System.out.println("print");
      return MyEnum.B;
    }
  }
}
