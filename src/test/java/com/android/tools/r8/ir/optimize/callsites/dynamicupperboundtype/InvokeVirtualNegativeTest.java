// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites.dynamicupperboundtype;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeVirtualNegativeTest extends TestBase {
  private static final Class<?> MAIN = Main.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeVirtualNegativeTest.class)
        .addKeepMainRule(MAIN)
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(
            o ->
                o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("A:Sub1", "A:Sub2", "B:Sub1", "B:Sub2")
        .inspect(this::inspect);
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    String methodName = method.getReference().name.toString();
    assert methodName.equals("m") || methodName.equals("test")
        : "Unexpected revisit: " + method.toSourceString();
    CallSiteOptimizationInfo callSiteOptimizationInfo =
        method.getOptimizationInfo().getArgumentInfos();
    if (methodName.equals("m")) {
      assertTrue(callSiteOptimizationInfo.getDynamicType(1).isNotNullType());
    } else {
      assertTrue(methodName.equals("test"));
      assertTrue(
          callSiteOptimizationInfo
              .getDynamicType(0)
              .asDynamicTypeWithUpperBound()
              .getDynamicUpperBoundType()
              .isDefinitelyNotNull());
    }
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject a = inspector.clazz(A.class);
    assertThat(a, isPresent());

    MethodSubject a_m = a.uniqueMethodWithOriginalName("m");
    assertThat(a_m, isPresent());
    // Should not optimize branches since the type of `arg` is unsure.
    assertTrue(a_m.streamInstructions().anyMatch(InstructionSubject::isIf));

    ClassSubject b = inspector.clazz(B.class);
    assertThat(b, isPresent());

    MethodSubject b_m = b.uniqueMethodWithOriginalName("m");
    assertThat(b_m, isPresent());
    // Should not optimize branches since the type of `arg` is unsure.
    assertTrue(b_m.streamInstructions().anyMatch(InstructionSubject::isIf));

    // Should not optimize away Sub1, since it's still referred/instantiated.
    ClassSubject sub1 = inspector.clazz(Sub1.class);
    assertThat(sub1, isPresent());

    // Should not optimize away Sub2, since it's still referred/instantiated.
    ClassSubject sub2 = inspector.clazz(Sub2.class);
    assertThat(sub2, isPresent());
  }

  static class Base {}
  static class Sub1 extends Base {}
  static class Sub2 extends Base {}

  @NoVerticalClassMerging
  @NeverClassInline
  static class A {
    @NeverInline
    void m(Base arg) {
      if (arg instanceof Sub1) {
        System.out.println("A:Sub1");
      } else if (arg instanceof Sub2) {
        System.out.println("A:Sub2");
      }
    }
  }

  @NeverClassInline
  static class B extends A {
    @NeverInline
    @Override
    void m(Base arg) {
      if (arg instanceof Sub1) {
        System.out.println("B:Sub1");
      } else if (arg instanceof Sub2) {
        System.out.println("B:Sub2");
      }
    }
  }

  static class Main {
    public static void main(String... args) {
      Sub2 s2 = new Sub2();

      A a = new A();
      test(a); // calls A.m() with Sub1.
      a.m(s2); // calls A.m() with Sub2.

      B b = new B();
      test(b); // calls B.m() with Sub1.
      b.m(s2); // calls B.m() with Sub2.
    }

    @NeverInline
    static void test(A arg) {
      arg.m(new Sub1());
    }
  }
}