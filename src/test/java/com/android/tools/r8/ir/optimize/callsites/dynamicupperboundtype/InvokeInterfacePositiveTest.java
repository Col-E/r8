// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites.dynamicupperboundtype;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
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
public class InvokeInterfacePositiveTest extends TestBase {
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
        .addInnerClasses(InvokeInterfacePositiveTest.class)
        .addKeepMainRule(MAIN)
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(
            o -> {
              // To prevent invoke-interface from being rewritten to invoke-virtual w/ a single
              // target.
              o.enableDevirtualization = false;
              o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect;
            })
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("A:Sub1", "B:Sub2")
        .inspect(this::inspect);
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    assert method.getReference().name.toString().equals("m")
        : "Unexpected revisit: " + method.toSourceString();
    CallSiteOptimizationInfo callSiteOptimizationInfo =
        method.getOptimizationInfo().getArgumentInfos();
    DynamicType dynamicType = callSiteOptimizationInfo.getDynamicType(1);
    if (method.getHolderType().toSourceString().endsWith("$A")) {
      TypeElement upperBoundType =
          callSiteOptimizationInfo
              .getDynamicType(1)
              .asDynamicTypeWithUpperBound()
              .getDynamicUpperBoundType();
      assert upperBoundType.isDefinitelyNotNull();
      assert upperBoundType.isClassType()
          && upperBoundType.asClassType().getClassType().toSourceString().endsWith("$Sub1");
    } else {
      assertTrue(dynamicType.isNotNullType());
    }
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject i = inspector.clazz(I.class);
    assertThat(i, isPresent());

    ClassSubject a = inspector.clazz(A.class);
    assertThat(a, isPresent());

    MethodSubject a_m = a.uniqueMethodWithOriginalName("m");
    assertThat(a_m, isPresent());
    // Can optimize branches since the type of `arg` is Sub1.
    assertTrue(a_m.streamInstructions().noneMatch(InstructionSubject::isIf));

    ClassSubject b = inspector.clazz(B.class);
    assertThat(b, isPresent());

    MethodSubject b_m = b.uniqueMethodWithOriginalName("m");
    assertThat(b_m, isPresent());
    // Should not optimize branches since the type of `arg` is unsure.
    assertTrue(b_m.streamInstructions().anyMatch(InstructionSubject::isIf));
  }

  @NoVerticalClassMerging
  static class Base {}

  static class Sub1 extends Base {}
  static class Sub2 extends Base {}

  interface I {
    void m(Base arg);
  }

  @NeverClassInline
  static class A implements I {
    @NeverInline
    @Override
    public void m(Base arg) {
      if (arg instanceof Sub1) {
        System.out.println("A:Sub1");
      } else if (arg instanceof Sub2) {
        System.out.println("A:Sub2");
      }
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class B implements I {
    @NeverInline
    @Override
    public void m(Base arg) {
      if (arg instanceof Sub1) {
        System.out.println("B:Sub1");
      } else if (arg instanceof Sub2) {
        System.out.println("B:Sub2");
      }
    }
  }

  static class Main {
    public static void main(String... args) {
      I i = System.currentTimeMillis() > 0 ? new A() : new B();
      i.m(new Sub1());  // calls A.m() with Sub1.
      i = new B();      // with the exact type:
      i.m(new Sub2());  // calls B.m() with Sub2.
    }
  }
}
