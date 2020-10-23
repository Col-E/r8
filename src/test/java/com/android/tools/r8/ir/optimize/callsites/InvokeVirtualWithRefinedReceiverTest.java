// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
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
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeVirtualWithRefinedReceiverTest extends TestBase {
  private static final Class<?> MAIN = Main.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public InvokeVirtualWithRefinedReceiverTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeVirtualWithRefinedReceiverTest.class)
        .addKeepMainRule(MAIN)
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(
            o -> {
              o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect;
            })
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("null", "C")
        .inspect(this::inspect);
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    assert method.getReference().name.toString().equals("m")
        : "Unexpected revisit: " + method.toSourceString();
    CallSiteOptimizationInfo callSiteOptimizationInfo =
        method.getDefinition().getCallSiteOptimizationInfo();
    if (method.getHolderType().toSourceString().endsWith("$C")) {
      assert callSiteOptimizationInfo.getDynamicUpperBoundType(1).isDefinitelyNotNull();
    } else {
      assert callSiteOptimizationInfo.getDynamicUpperBoundType(1).isDefinitelyNull();
    }
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject a = inspector.clazz(A.class);
    assertThat(a, isPresent());

    ClassSubject b = inspector.clazz(B.class);
    assertThat(b, isPresent());

    MethodSubject b_m = b.uniqueMethodWithName("m");
    assertThat(b_m, isPresent());
    // Can optimize branches since `arg` is definitely null.
    assertTrue(b_m.streamInstructions().noneMatch(InstructionSubject::isIf));

    ClassSubject bSub = inspector.clazz(BSub.class);
    assertThat(bSub, isPresent());

    MethodSubject bSub_m = bSub.uniqueMethodWithName("m");
    assertThat(bSub_m, isPresent());
    // Can optimize branches since `arg` is definitely null.
    assertTrue(bSub_m.streamInstructions().noneMatch(InstructionSubject::isIf));

    ClassSubject c = inspector.clazz(C.class);
    assertThat(c, isPresent());

    MethodSubject c_m = c.uniqueMethodWithName("m");
    assertThat(c_m, isPresent());
    // Can optimize branches since `arg` is definitely not null.
    assertTrue(c_m.streamInstructions().noneMatch(InstructionSubject::isIf));

    ClassSubject cSub = inspector.clazz(CSub.class);
    assertThat(cSub, not(isPresent()));
  }

  abstract static class A {
    abstract void m(Object arg);
  }

  @NoVerticalClassMerging
  @NeverClassInline
  static class B extends A {
    @NeverInline
    @Override
    void m(Object arg) {
      // Technically same as String#valueOf.
      if (arg != null) {
        System.out.println(arg.toString());
      } else {
        System.out.println("null");
      }
    }

    @NeverInline
    @Override
    public String toString() {
      return "B";
    }
  }

  @NeverClassInline
  static class BSub extends B {
    @NeverInline
    @Override
    void m(Object arg) {
      // Same as B#m.
      if (arg != null) {
        System.out.println(arg.toString());
      } else {
        System.out.println("null");
      }
    }

    @NeverInline
    @Override
    public String toString() {
      return "BSub";
    }
  }

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  @NeverClassInline
  static class C extends A {
    @NeverInline
    @Override
    void m(Object arg) {
      // Technically same as String#valueOf.
      if (arg != null) {
        System.out.println(arg.toString());
      } else {
        System.out.println("null");
      }
    }

    @NeverInline
    @Override
    public String toString() {
      return "C";
    }
  }

  @NeverClassInline
  static class CSub extends C {
    @NeverInline
    @Override
    void m(Object arg) {
      // Same as C#m.
      if (arg != null) {
        System.out.println(arg.toString());
      } else {
        System.out.println("null");
      }
    }

    @NeverInline
    @Override
    public String toString() {
      return "CSub";
    }
  }

  static class Main {
    public static void main(String... args) {
      A a = System.currentTimeMillis() > 0 ? new B() : new BSub();
      a.m(null);  // No single target, but should be able to filter out C(Sub)#m

      A c = new C();  // with the exact type:
      c.m(c);         // calls C.m() with non-null instance.
    }
  }
}
