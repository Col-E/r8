// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BridgeWithUnboxingInliningTest extends TestBase {

  @Parameter(0)
  public boolean enableSimpleInliningInstructionLimitIncrement;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, enable increment: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        // Disable multi caller inlining.
        .addOptionsModification(
            options -> {
              InlinerOptions inlinerOptions = options.inlinerOptions();
              inlinerOptions.enableSimpleInliningInstructionLimitIncrement =
                  enableSimpleInliningInstructionLimitIncrement;
              inlinerOptions.multiCallerInliningInstructionLimits = new int[0];
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(StringUtils.times(StringUtils.lines("1", "2", "3", "4", "5"), 2));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(Main.class);
    assertThat(classSubject, isPresent());

    MethodSubject barMethodSubject = classSubject.uniqueMethodWithOriginalName("bar");
    assertThat(barMethodSubject, isPresent());

    MethodSubject fooMethodSubject = classSubject.uniqueMethodWithOriginalName("foo");
    assertThat(fooMethodSubject, isPresent());
    assertThat(fooMethodSubject, invokesMethod(barMethodSubject));

    MethodSubject invokeWithATestMethodSubject =
        classSubject.uniqueMethodWithOriginalName("invokeWithPrimitiveTest");
    assertThat(invokeWithATestMethodSubject, isPresent());
    assertThat(
        invokeWithATestMethodSubject,
        invokesMethod(
            enableSimpleInliningInstructionLimitIncrement ? barMethodSubject : fooMethodSubject));

    MethodSubject invokeWithBoxedPrimitiveTestMethodSubject =
        classSubject.uniqueMethodWithOriginalName("invokeWithBoxedPrimitiveTest");
    assertThat(invokeWithBoxedPrimitiveTestMethodSubject, isPresent());
    assertThat(invokeWithBoxedPrimitiveTestMethodSubject, invokesMethod(fooMethodSubject));
  }

  static class Main {

    public static void main(String[] args) {
      invokeWithPrimitiveTest();
      invokeWithBoxedPrimitiveTest();
    }

    @NeverInline
    static void invokeWithPrimitiveTest() {
      Integer o1 = 1;
      Integer o2 = 2;
      Integer o3 = 3;
      Integer o4 = 4;
      Integer o5 = 5;
      foo(o1, o2, o3, o4, o5);
    }

    @NeverInline
    static void invokeWithBoxedPrimitiveTest() {
      Integer o1 = System.currentTimeMillis() > 0 ? 1 : null;
      Integer o2 = System.currentTimeMillis() > 0 ? 2 : null;
      Integer o3 = System.currentTimeMillis() > 0 ? 3 : null;
      Integer o4 = System.currentTimeMillis() > 0 ? 4 : null;
      Integer o5 = System.currentTimeMillis() > 0 ? 5 : null;
      foo(o1, o2, o3, o4, o5);
    }

    static void foo(Integer o1, Integer o2, Integer o3, Integer o4, Integer o5) {
      int i1 = o1;
      int i2 = o2;
      int i3 = o3;
      int i4 = o4;
      int i5 = o5;
      bar(i1, i2, i3, i4, i5);
    }

    @NeverInline
    static void bar(int i1, int i2, int i3, int i4, int i5) {
      System.out.println(i1);
      System.out.println(i2);
      System.out.println(i3);
      System.out.println(i4);
      System.out.println(i5);
    }
  }

  @NeverClassInline
  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }
}
