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
public class OverloadingEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public OverloadingEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = TestClass.class;
    testForR8(parameters.getBackend())
        .addInnerClasses(OverloadingEnumUnboxingTest.class)
        .addKeepMainRule(classToTest)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(
            inspector -> inspector.assertUnboxed(MyEnum1.class, MyEnum2.class))
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
  enum MyEnum1 {
    A,
    B,
    C;
  }

  @NeverClassInline
  enum MyEnum2 {
    A,
    B,
    C;
  }

  @NeverClassInline
  enum MyEnum3 {
    A,
    B,
    C;
  }

  static class TestClass {

    public static void main(String[] args) {
      virtualTest();
      staticTest();
      constructorTest();
    }

    @NeverInline
    private static void constructorTest() {
      new TestClass(42);
      System.out.println("42");
      new TestClass(MyEnum1.A);
      System.out.println("0");
      new TestClass(MyEnum1.B);
      System.out.println("1");
      new TestClass(MyEnum2.A);
      System.out.println("0");
      new TestClass(MyEnum2.B);
      System.out.println("1");
      new TestClass(MyEnum3.A);
      System.out.println("0");
      new TestClass(MyEnum3.B);
      System.out.println("1");
    }

    @NeverInline
    private static void staticTest() {
      staticMethod(42);
      System.out.println("42");
      staticMethod(MyEnum1.A);
      System.out.println("0");
      staticMethod(MyEnum1.B);
      System.out.println("1");
      staticMethod(MyEnum2.A);
      System.out.println("0");
      staticMethod(MyEnum2.B);
      System.out.println("1");
      staticMethod(MyEnum3.A);
      System.out.println("0");
      staticMethod(MyEnum3.B);
      System.out.println("1");
    }

    @NeverInline
    private static void virtualTest() {
      TestClass testClass = new TestClass();
      testClass.virtualMethod(42);
      System.out.println("42");
      testClass.virtualMethod(MyEnum1.A);
      System.out.println("0");
      testClass.virtualMethod(MyEnum1.B);
      System.out.println("1");
      testClass.virtualMethod(MyEnum2.A);
      System.out.println("0");
      testClass.virtualMethod(MyEnum2.B);
      System.out.println("1");
      testClass.virtualMethod(MyEnum3.A);
      System.out.println("0");
      testClass.virtualMethod(MyEnum3.B);
      System.out.println("1");
    }

    public TestClass() {}

    @NeverInline
    public TestClass(MyEnum1 e1) {
      System.out.println(e1.ordinal());
    }

    @NeverInline
    public TestClass(MyEnum2 e2) {
      System.out.println(e2.ordinal());
    }

    @NeverInline
    public TestClass(MyEnum3 e3) {
      System.out.println(e3.ordinal());
    }

    @NeverInline
    public TestClass(int i) {
      System.out.println(i);
    }

    @NeverInline
    public void virtualMethod(MyEnum1 e1) {
      System.out.println(e1.ordinal());
    }

    @NeverInline
    public void virtualMethod(MyEnum2 e2) {
      System.out.println(e2.ordinal());
    }

    @NeverInline
    public void virtualMethod(MyEnum3 e3) {
      System.out.println(e3.ordinal());
    }

    @NeverInline
    public void virtualMethod(int i) {
      System.out.println(i);
    }

    @NeverInline
    public static void staticMethod(MyEnum1 e1) {
      System.out.println(e1.ordinal());
    }

    @NeverInline
    public static void staticMethod(MyEnum2 e2) {
      System.out.println(e2.ordinal());
    }

    @NeverInline
    public static void staticMethod(MyEnum3 e3) {
      System.out.println(e3.ordinal());
    }

    @NeverInline
    public static void staticMethod(int i) {
      System.out.println(i);
    }
  }
}
