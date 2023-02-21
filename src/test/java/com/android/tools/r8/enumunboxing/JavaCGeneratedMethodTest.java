// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class JavaCGeneratedMethodTest extends EnumUnboxingTestBase {

  private static final Class<MyEnum> ENUM_CLASS = MyEnum.class;

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameterized.Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public JavaCGeneratedMethodTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = Ordinal.class;
    testForR8(parameters.getBackend())
        .addProgramClasses(classToTest, ENUM_CLASS)
        .addKeepMainRule(classToTest)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(ENUM_CLASS))
        .enableNeverClassInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addOptionsModification(opt -> opt.testing.enumUnboxingRewriteJavaCGeneratedMethod = true)
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

  static class Ordinal {

    public static void main(String[] args) {
      System.out.println(MyEnum.A.ordinal());
      System.out.println(0);
    }
  }
}
