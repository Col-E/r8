// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VirtualMethodOverrideEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public VirtualMethodOverrideEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .addOptionsModification(options -> enableEnumOptions(options, enumValueOptimization))
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "Middle.m(A : MyEnum, 42 : int)",
            "Bottom.m(A : MyEnum, 42 : int)",
            "Bottom.m(42 : int, A : MyEnum)",
            "Middle.m(A : MyEnum, 42 : int)",
            "Middle2.m(A : MyEnum, 42 : int)",
            "Middle.m(A : MyEnum, 42 : int)",
            "Bottom.m(A : MyEnum, 42 : int)",
            "Middle.m(A : MyEnum, 42 : int)",
            "Middle2.m(A : MyEnum, 42 : int)",
            "Something");
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject methodOnMiddle = inspector.clazz(Middle.class).virtualMethods().get(0);
    MethodSubject methodOnBottom =
        inspector.clazz(Bottom.class).uniqueMethodWithFinalName(methodOnMiddle.getFinalName());
    assertThat(methodOnBottom, isPresent());
    assertTrue(
        methodOnBottom
            .streamInstructions()
            .anyMatch(
                i ->
                    i.asDexInstruction().isInvokeSuper()
                        && i.getMethod() == methodOnMiddle.getMethod().getReference()));
  }

  static class TestClass {

    public static void main(String[] args) {
      MyEnum value = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      new Bottom().m(value, 42);
      new Bottom().m(42, value);
      new Middle().m(value, 42);
      new Middle2().m(value, 42);
      fromTop(new Bottom(), value);
      fromTop(new Middle(), value);
      fromTop(new Middle2(), value);
      new Middle().checkNotNullOrPrintSomething(value);
      new Bottom().checkNotNullOrPrintSomething(value);
    }

    @NeverInline
    public static void fromTop(Top top, MyEnum value) {
      top.m(value, 42);
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  abstract static class Top {

    @NeverInline
    abstract void m(MyEnum x, int y);
  }

  @NeverClassInline
  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  static class Middle2 extends Top {
    @NeverInline
    void m(MyEnum x, int y) {
      System.out.println("Middle2.m(" + x.toString() + " : MyEnum, " + y + " : int)");
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  static class Middle extends Top {

    @NeverInline
    void m(MyEnum x, int y) {
      System.out.println("Middle.m(" + x.toString() + " : MyEnum, " + y + " : int)");
    }

    @NeverInline
    void checkNotNullOrPrintSomething(MyEnum e) {
      if (e == null) {
        throw new NullPointerException();
      }
    }
  }

  @NeverClassInline
  static class Bottom extends Middle {

    @KeepConstantArguments
    @NeverInline
    @NoMethodStaticizing
    void m(int x, MyEnum y) {
      System.out.println("Bottom.m(" + x + " : int, " + y.toString() + " : MyEnum)");
    }

    @KeepConstantArguments
    @NeverInline
    @Override
    void m(MyEnum x, int y) {
      super.m(x, y);
      System.out.println("Bottom.m(" + x.toString() + " : MyEnum, " + y + " : int)");
    }

    @KeepConstantArguments
    @NeverInline
    @Override
    void checkNotNullOrPrintSomething(MyEnum e) {
      System.out.println("Something");
    }
  }

  enum MyEnum {
    A,
    B
  }
}
