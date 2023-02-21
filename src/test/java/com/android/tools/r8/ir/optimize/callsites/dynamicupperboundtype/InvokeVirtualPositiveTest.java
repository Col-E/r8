// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites.dynamicupperboundtype;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
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
public class InvokeVirtualPositiveTest extends TestBase {
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
        .addInnerClasses(InvokeVirtualPositiveTest.class)
        .addKeepMainRule(MAIN)
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(
            o ->
                o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("A:Sub1", "B:Sub1")
        .inspect(this::inspect);
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    String methodName = method.getReference().name.toString();
    assert methodName.equals("<init>") || methodName.equals("m")
        : "Unexpected revisit: " + method.toSourceString();
    CallSiteOptimizationInfo callSiteOptimizationInfo =
        method.getOptimizationInfo().getArgumentInfos();
    TypeElement upperBoundType;
    if (methodName.equals("m")) {
      upperBoundType =
          callSiteOptimizationInfo
              .getDynamicType(1)
              .asDynamicTypeWithUpperBound()
              .getDynamicUpperBoundType();
    } else {
      // TODO(b/139246447): should avoid visiting <init>, which is trivial, default init!
      // For testing purpose, `Base` is not merged and kept. The system correctly caught that, when
      // the default initializer is invoked, the receiver had a refined type, `Sub1`.
      upperBoundType =
          callSiteOptimizationInfo
              .getDynamicType(0)
              .asDynamicTypeWithUpperBound()
              .getDynamicUpperBoundType();
    }
    assert upperBoundType.isDefinitelyNotNull();
    assert upperBoundType.isClassType()
        && upperBoundType.asClassType().getClassType().toSourceString().endsWith("$Sub1");
  }

  private void inspect(CodeInspector inspector) {
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
    // Can optimize branches since the type of `arg` is Sub1.
    assertTrue(b_m.streamInstructions().noneMatch(InstructionSubject::isIf));
  }

  @NoVerticalClassMerging
  static class Base {}

  static class Sub1 extends Base {}
  static class Sub2 extends Base {}

  @NoVerticalClassMerging
  @NeverClassInline
  static class A {
    @NoParameterTypeStrengthening
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
      Sub1 s1 = new Sub1();

      A a = System.currentTimeMillis() > 0 ? new A() : new B();
      a.m(s1); // calls A.m() with Sub1.

      B b = new B();
      b.m(s1); // calls B.m() with Sub1.
    }
  }
}
