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
public class BridgeWithCastsInliningTest extends TestBase {

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
        .assertSuccessWithOutput(StringUtils.times(StringUtils.lines("A"), 10));
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
        classSubject.uniqueMethodWithOriginalName("invokeWithATest");
    assertThat(invokeWithATestMethodSubject, isPresent());
    assertThat(
        invokeWithATestMethodSubject,
        invokesMethod(
            enableSimpleInliningInstructionLimitIncrement ? barMethodSubject : fooMethodSubject));

    MethodSubject invokeWithObjectTestMethodSubject =
        classSubject.uniqueMethodWithOriginalName("invokeWithObjectTest");
    assertThat(invokeWithObjectTestMethodSubject, isPresent());
    assertThat(invokeWithObjectTestMethodSubject, invokesMethod(fooMethodSubject));
  }

  static class Main {

    public static void main(String[] args) {
      invokeWithATest();
      invokeWithObjectTest();
    }

    @NeverInline
    static void invokeWithATest() {
      A a = new A();
      foo(a, a, a, a, a);
    }

    @NeverInline
    static void invokeWithObjectTest() {
      Object o = System.currentTimeMillis() > 0 ? new A() : new Object();
      foo(o, o, o, o, o);
    }

    static void foo(Object o1, Object o2, Object o3, Object o4, Object o5) {
      A a1 = (A) o1;
      A a2 = (A) o2;
      A a3 = (A) o3;
      A a4 = (A) o4;
      A a5 = (A) o5;
      bar(a1, a2, a3, a4, a5);
    }

    @NeverInline
    static void bar(A a1, A a2, A a3, A a4, A a5) {
      System.out.println(a1);
      System.out.println(a2);
      System.out.println(a3);
      System.out.println(a4);
      System.out.println(a5);
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
