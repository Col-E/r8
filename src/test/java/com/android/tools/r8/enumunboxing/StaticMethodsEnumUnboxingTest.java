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
public class StaticMethodsEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public StaticMethodsEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = StaticMethods.class;
    testForR8(parameters.getBackend())
        .addInnerClasses(StaticMethodsEnumUnboxingTest.class)
        .addKeepMainRule(classToTest)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class, MyEnum2.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), classToTest)
        .assertSuccess()
        .inspectStdOut(this::assertLines2By2Correct);
  }

  @SuppressWarnings("SameParameterValue")
  @NeverClassInline
  enum MyEnum {
    A,
    B,
    C;

    @NeverInline
    public static void print(Object o) {
      System.out.println(o);
    }

    @NeverInline
    public static void printEnum(MyEnum e) {
      System.out.println(e.ordinal());
    }

    @NeverInline
    public static MyEnum returnEnum(boolean bool) {
      return bool ? MyEnum.A : MyEnum.B;
    }

    @NeverInline
    protected static void printProtected() {
      System.out.println("protected");
    }

    @NeverInline
    static void printPackagePrivate() {
      System.out.println("package-private");
    }

    @NeverInline
    private static void printPrivate() {
      System.out.println("private");
    }

    @NeverInline
    public static void callPrivate() {
      System.out.print("call: ");
      printPrivate();
    }
  }

  // Use two enums to test collision between values and valueOf.
  enum MyEnum2 {
    A,
    B,
    C;
  }

  static class StaticMethods {

    public static void main(String[] args) {
      testCustomMethods();
      testNonPublicMethods();
      testGeneratedMethods();
      testGeneratedMethods2();
    }

    @NeverInline
    private static void testNonPublicMethods() {
      MyEnum.printPrivate();
      System.out.println("private");
      MyEnum.printPackagePrivate();
      System.out.println("package-private");
      MyEnum.printProtected();
      System.out.println("protected");
      MyEnum.callPrivate();
      System.out.println("call: private");
    }

    @NeverInline
    private static void testCustomMethods() {
      MyEnum.print("print");
      System.out.println("print");
      MyEnum.printEnum(MyEnum.A);
      System.out.println(0);
      System.out.println((MyEnum.returnEnum(true).ordinal()));
      System.out.println(0);
    }

    @NeverInline
    private static void testGeneratedMethods() {
      System.out.println(MyEnum.valueOf("C").ordinal());
      System.out.println(2);
      System.out.println(MyEnum.values()[0].ordinal());
      System.out.println(0);
    }

    @NeverInline
    private static void testGeneratedMethods2() {
      System.out.println(MyEnum2.valueOf("C").ordinal());
      System.out.println(2);
      System.out.println(MyEnum2.values()[0].ordinal());
      System.out.println(0);
    }
  }
}
