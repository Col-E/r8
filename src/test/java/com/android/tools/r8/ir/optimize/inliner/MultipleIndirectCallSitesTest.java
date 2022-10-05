// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MultipleIndirectCallSitesTest extends TestBase {

  private final boolean invokeMethodOnA;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, invoke A.m(): {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public MultipleIndirectCallSitesTest(boolean invokeMethodOnA, TestParameters parameters) {
    this.invokeMethodOnA = invokeMethodOnA;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(MultipleIndirectCallSitesTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRulesWithAllowObfuscation(B.class)
        // Keep invokeMethodOnA() if specified by the test configuration. If invokeMethodOnA() is
        // kept, there is a single call site that invokes A.m(). However, this does not mean that
        // A.m() has a single call site, so it should not allow inlining of A.m().
        .addKeepRules(getKeepRules())
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::verifyInlining)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A.m()", "A.m()", "A.m()", "A.m()", "A.m()");
  }

  private List<String> getKeepRules() {
    if (invokeMethodOnA) {
      return ImmutableList.of(
          "-keep class " + TestClass.class.getName() + " { void invokeMethodOnA(); }");
    }
    return ImmutableList.of();
  }

  private void verifyInlining(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject.mainMethod(), isPresent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    MethodSubject methodSubject = aClassSubject.uniqueMethodWithOriginalName("m");
    assertThat(methodSubject, isPresent());
    assertEquals(
        5,
        testClassSubject
            .mainMethod()
            .streamInstructions()
            .filter(
                instruction ->
                    instruction.isInvokeVirtual()
                        && instruction
                            .getMethod()
                            .name
                            .toSourceString()
                            .equals(methodSubject.getFinalName()))
            .count());
  }

  static class TestClass {

    public static void main(String[] args) {
      I instance = new A();
      instance.m();
      instance.m();
      instance.m();
      instance.m();
      instance.m();
    }

    static void invokeMethodOnA() {
      new A().m();
    }
  }

  interface I {

    void m();
  }

  @NeverClassInline
  static class A implements I {

    @Override
    public void m() {
      System.out.print("A");
      System.out.print(".");
      System.out.print("m");
      System.out.print("(");
      System.out.println(")");
    }
  }

  static class B implements I {

    @Override
    public void m() {
      System.out.println("B.m()");
    }
  }
}
