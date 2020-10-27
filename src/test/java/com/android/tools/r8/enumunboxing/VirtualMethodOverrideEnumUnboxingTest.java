// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
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
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addOptionsModification(options -> enableEnumOptions(options, enumValueOptimization))
        .addOptionsModification(options -> options.testing.enableEnumUnboxingDebugLogs = false)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "A.m(A : MyEnum, 42 : int)", "B.m(A : MyEnum, 42 : int)", "B.m(42 : int, A : MyEnum)");
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject methodOnA = inspector.clazz(A.class).virtualMethods().get(0);
    MethodSubject methodOnB =
        inspector.clazz(B.class).uniqueMethodWithFinalName(methodOnA.getFinalName());
    assertThat(methodOnB, isPresent());
    // TODO(b/171784168): Should be true.
    assertFalse(methodOnB.streamInstructions().anyMatch(x -> x.asDexInstruction().isInvokeSuper()));
  }

  static class TestClass {

    public static void main(String[] args) {
      MyEnum value = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      new B().m(value, 42);
      new B().m(42, value);
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class A {

    @NeverInline
    void m(MyEnum x, int y) {
      System.out.println("A.m(" + x.toString() + " : MyEnum, " + y + " : int)");
    }
  }

  @NeverClassInline
  static class B extends A {

    @NeverInline
    void m(int x, MyEnum y) {
      System.out.println("B.m(" + x + " : int, " + y.toString() + " : MyEnum)");
    }

    @NeverInline
    @Override
    void m(MyEnum x, int y) {
      super.m(x, y);
      System.out.println("B.m(" + x.toString() + " : MyEnum, " + y + " : int)");
    }
  }

  enum MyEnum {
    A,
    B
  }
}
