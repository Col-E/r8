// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.dynamictype;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DynamicTypeOptimizationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public DynamicTypeOptimizationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(DynamicTypeOptimizationTest.class)
        .addKeepMainRule(TestClass.class)
        // Keep B to ensure that we will treat it as being instantiated.
        .addKeepClassRulesWithAllowObfuscation(B.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.lines("Hello world!"));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject mainClassSubject = inspector.clazz(TestClass.class);
    assertThat(mainClassSubject, isPresent());

    ClassSubject interfaceSubject = inspector.clazz(I.class);
    assertThat(interfaceSubject, isPresent());

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    // Verify that the check-cast instruction is still present in testCheckCastRemoval().
    MethodSubject testCheckCastRemovalMethod =
        mainClassSubject.uniqueMethodWithName("testCheckCastRemoval");
    assertThat(testCheckCastRemovalMethod, isPresent());
    assertTrue(
        testCheckCastRemovalMethod
            .streamInstructions()
            .anyMatch(instruction -> instruction.isCheckCast(aClassSubject.getFinalName())));

    // Verify that the instance-of instruction is only present in testInstanceOfRemoval() if the
    // dynamic type optimization is disabled.
    MethodSubject testInstanceOfRemovalMethod =
        mainClassSubject.uniqueMethodWithName("testInstanceOfRemoval");
    assertThat(testInstanceOfRemovalMethod, isPresent());
    assertTrue(
        testInstanceOfRemovalMethod
            .streamInstructions()
            .noneMatch(instruction -> instruction.isInstanceOf(aClassSubject.getFinalName())));

    // Verify that world() has been inlined() into testMethodInlining() unless the dynamic type
    // optimization is disabled.
    MethodSubject testMethodInliningMethod =
        mainClassSubject.uniqueMethodWithName("testMethodInlining");
    assertThat(testMethodInliningMethod, isPresent());
    assertTrue(interfaceSubject.uniqueMethodWithName("world").isAbsent());

    // Verify that exclamationMark() has been rebound in testMethodRebinding() unless the dynamic
    // type optimization is disabled.
    MethodSubject testMethodRebindingMethod =
        mainClassSubject.uniqueMethodWithName("testMethodRebinding");
    assertThat(testMethodRebindingMethod, isPresent());
    assertThat(
        testMethodRebindingMethod,
        invokesMethod(aClassSubject.uniqueMethodWithName("exclamationMark")));
  }

  static class TestClass {

    public static void main(String[] args) {
      testCheckCastRemoval();
      testInstanceOfRemoval();
      testMethodInlining();
      testMethodRebinding();
    }

    @NeverInline
    private static void testCheckCastRemoval() {
      // Used to verify that we do not remove check-cast instructions, since these are sometimes
      // required for the program to pass the static type checker.
      A obj = (A) get();
      obj.hello();
    }

    @NeverInline
    private static void testInstanceOfRemoval() {
      // Used to verify that we remove trivial instance-of instructions.
      if (get() instanceof A) {
        System.out.print(" ");
      }
    }

    @NeverInline
    private static void testMethodInlining() {
      // Used to verify that we identify a single target despite of the imprecise static type.
      get().world();
    }

    @NeverInline
    private static void testMethodRebinding() {
      // Used to verify that we rebind to the most specific target when inlining is not possible.
      get().exclamationMark();
    }

    @NeverInline
    private static I get() {
      return new A();
    }
  }

  interface I {

    void hello();

    void world();

    @NeverInline
    void exclamationMark();
  }

  static class A implements I {

    @NeverInline
    @Override
    public void hello() {
      System.out.print("Hello");
    }

    @Override
    public void world() {
      System.out.print("world");
    }

    @NeverInline
    @Override
    public void exclamationMark() {
      System.out.println("!");
    }
  }

  static class B implements I {

    @Override
    public void hello() {
      System.out.println("Unreachable");
    }

    @Override
    public void world() {
      System.out.println("Unreachable");
    }

    @NeverInline
    @Override
    public void exclamationMark() {
      System.out.println("Unreachable");
    }
  }
}
