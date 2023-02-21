// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.checkcast;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TrivialTypeTestsAfterBranchPruningTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public TrivialTypeTestsAfterBranchPruningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(TrivialTypeTestsAfterBranchPruningTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("dead"), not(isPresent()));

    MethodSubject trivialCastMethodSubject =
        classSubject.uniqueMethodWithOriginalName("trivialCastAfterBranchPruningTest");
    assertThat(trivialCastMethodSubject, isPresent());
    assertTrue(
        trivialCastMethodSubject.streamInstructions().noneMatch(InstructionSubject::isCheckCast));

    MethodSubject branchPruningMethodSubject =
        classSubject.uniqueMethodWithOriginalName("branchPruningAfterInstanceOfOptimization");
    assertThat(branchPruningMethodSubject, isPresent());
    assertTrue(
        branchPruningMethodSubject
            .streamInstructions()
            .noneMatch(InstructionSubject::isInstanceOf));
  }

  static class TestClass {

    static boolean alwaysTrue = true;

    public static void main(String[] args) {
      branchPruningAfterInstanceOfOptimization(trivialCastAfterBranchPruningTest());
    }

    @NeverInline
    static A trivialCastAfterBranchPruningTest() {
      return (A) (alwaysTrue ? new A() : new B());
    }

    @NeverInline
    static void branchPruningAfterInstanceOfOptimization(A obj) {
      if (obj instanceof A) {
        System.out.println("Hello world!");
      } else {
        dead();
      }
    }

    @NeverInline
    static void dead() {
      throw new RuntimeException();
    }
  }

  static class A {}

  static class B {}
}
