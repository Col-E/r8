// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClInitSideEffectEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public ClInitSideEffectEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<Switch> classToTest = Switch.class;
    testForR8(parameters.getBackend())
        .addInnerClasses(ClInitSideEffectEnumUnboxingTest.class)
        .addKeepMainRule(classToTest)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
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
    C;

    @NeverInline
    void print() {
      Switch.packagePrivatePrint();
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class OtherClass {
    static {
      Switch.otherClassInit = true;
    }
  }

  static class Switch {

    static boolean otherClassInit = false;

    public static void main(String[] args) {
      System.out.println(MyEnum.A.ordinal());
      System.out.println(0);
      System.out.println(MyEnum.B.ordinal());
      System.out.println(1);

      MyEnum.A.print();
      packagePrivatePrint();

      System.out.println(otherClassInit);
      System.out.println(false);

      initializeOtherClass();

      System.out.println(otherClassInit);
      System.out.println(true);
    }

    @NeverInline
    private static void initializeOtherClass() {
      new OtherClass();
    }

    @NeverInline
    static void packagePrivatePrint() {
      System.out.println("package private dependency");
    }
  }
}
