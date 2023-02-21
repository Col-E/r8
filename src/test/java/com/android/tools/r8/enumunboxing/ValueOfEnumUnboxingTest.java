// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ValueOfEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final Class<?>[] TESTS = {
    EnumValueOf.class,
  };

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public ValueOfEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addInnerClasses(ValueOfEnumUnboxingTest.class)
            .addKeepMainRules(TESTS)
            .addEnumUnboxingInspector(
                inspector -> inspector.assertUnboxed(EnumValueOf.MyEnum.class))
            .enableNeverClassInliningAnnotations()
            .addKeepRules(enumKeepRules.getKeepRules())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .setMinApi(parameters)
            .compile();
    for (Class<?> main : TESTS) {
      compile
          .run(parameters.getRuntime(), main)
          .assertSuccess()
          .inspectStdOut(this::assertLines2By2Correct);
    }
  }

  static class EnumValueOf {

    @NeverClassInline
    enum MyEnum {
      A,
      B
    }

    public static void main(String[] args) {
      System.out.println(Enum.valueOf(EnumValueOf.MyEnum.class, "A").ordinal());
      System.out.println(0);
      System.out.println(Enum.valueOf(EnumValueOf.MyEnum.class, "B").ordinal());
      System.out.println(1);
      try {
        iae();
      } catch (IllegalArgumentException argException) {
        System.out.println(argException.getMessage());
        System.out.println(
            "No enum constant"
                + " com.android.tools.r8.enumunboxing.ValueOfEnumUnboxingTest.EnumValueOf.MyEnum.C");
      }
      try {
        npe();
      } catch (NullPointerException npe) {
        System.out.println(npe.getMessage());
        System.out.println("Name is null");
      }
    }

    @SuppressWarnings("ConstantConditions")
    private static void npe() {
      Enum.valueOf(MyEnum.class, null);
    }

    private static void iae() {
      Enum.valueOf(MyEnum.class, "C");
    }
  }
}
