// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites.constants;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
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
        // To prevent invoke-interface from being rewritten to invoke-virtual w/ a single
        // target.
        .addOptionsModification(o -> o.enableDevirtualization = false)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with argument removal.
        .addDontObfuscate()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("non-null")
        .inspect(this::inspect);
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    assert method.getReference().name.toString().equals("m")
        : "Unexpected revisit: " + method.toSourceString();
    CallSiteOptimizationInfo callSiteOptimizationInfo =
        method.getOptimizationInfo().getArgumentInfos();
    assert callSiteOptimizationInfo.getDynamicType(1).getNullability().isDefinitelyNotNull();
    AbstractValue abstractValue = callSiteOptimizationInfo.getAbstractArgumentValue(1);
    assert abstractValue.isSingleStringValue()
        && abstractValue.asSingleStringValue().getDexString().toString().equals("nul");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());

    // Verify that the "nul" argument has been propagated to the m() methods.
    MethodSubject mainMethodSubject = main.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertTrue(mainMethodSubject.streamInstructions().noneMatch(InstructionSubject::isConstString));

    ClassSubject i = inspector.clazz(I.class);
    assertThat(i, isPresent());

    MethodSubject i_m = i.uniqueMethodWithOriginalName("m");
    assertThat(i_m, isPresent());
    assertEquals(0, i_m.getProgramMethod().getReference().getArity());

    ClassSubject a = inspector.clazz(A.class);
    assertThat(a, isPresent());

    MethodSubject a_m = a.uniqueMethodWithOriginalName("m");
    assertThat(a_m, isPresent());
    assertEquals(0, a_m.getProgramMethod().getReference().getArity());
    // Can optimize branches since `arg` is definitely "nul", i.e., not containing "null".
    assertTrue(a_m.streamInstructions().noneMatch(InstructionSubject::isIf));

    ClassSubject b = inspector.clazz(B.class);
    assertThat(b, isPresent());

    MethodSubject b_m = b.uniqueMethodWithOriginalName("m");
    assertThat(b_m, isPresent());
    assertEquals(0, b_m.getProgramMethod().getReference().getArity());
    // Can optimize branches since `arg` is definitely "nul", i.e., not containing "null".
    assertTrue(b_m.streamInstructions().noneMatch(InstructionSubject::isIf));
  }

  interface I {
    void m(String arg);
  }

  @NeverClassInline
  static class A implements I {
    @NeverInline
    @Override
    public void m(String arg) {
      if (arg.contains("null")) {
        System.out.println("null");
      } else {
        System.out.println("non-null");
      }
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class B implements I {
    @NeverInline
    @Override
    public void m(String arg) {
      if (arg.contains("null")) {
        System.out.println("null");
      } else {
        System.out.println("non-null");
      }
    }
  }

  static class Main {
    public static void main(String... args) {
      I i = System.currentTimeMillis() > 0 ? new A() : new B();
      i.m("nul");  // calls A.m() with "nul".
    }
  }
}
