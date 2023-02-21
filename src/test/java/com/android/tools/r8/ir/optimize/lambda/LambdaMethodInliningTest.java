// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
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

@RunWith(Parameterized.class)
public class LambdaMethodInliningTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public LambdaMethodInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(LambdaMethodInliningTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addOptionsModification(options -> options.enableClassInlining = false)
        // TODO(b/173398086): Horizontal class merging breaks uniqueMethodWithName().
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello", "Hello");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    MethodSubject testClassMethodSubject = classSubject.uniqueMethodWithOriginalName("testClass");
    assertThat(testClassMethodSubject, isPresent());
    assertTrue(
        testClassMethodSubject
            .streamInstructions()
            .noneMatch(InstructionSubject::isInvokeInterface));
    assertTrue(
        testClassMethodSubject
            .streamInstructions()
            .anyMatch(
                instruction ->
                    instruction.isInvokeVirtual()
                        && instruction.getMethod().toSourceString().contains("println")));

    MethodSubject testLambdaMethodSubject = classSubject.uniqueMethodWithOriginalName("testLambda");
    assertThat(testLambdaMethodSubject, isPresent());
    assertTrue(
        testLambdaMethodSubject
            .streamInstructions()
            .noneMatch(InstructionSubject::isInvokeInterface));
    assertTrue(
        testLambdaMethodSubject
            .streamInstructions()
            .anyMatch(
                instruction ->
                    instruction.isInvokeVirtual()
                        && instruction.getMethod().toSourceString().contains("println")));
  }

  static class TestClass {

    public static void main(String[] args) {
      testClass(new A());
      testLambda(
          () -> {
            System.out.print("H");
            System.out.print("e");
            System.out.print("l");
            System.out.print("l");
            System.out.println("o");
          });
    }

    @NeverInline
    static void testClass(ImplementedByClass obj) {
      obj.m();
    }

    @NeverInline
    static void testLambda(ImplementedByLambda obj) {
      obj.m();
    }
  }

  @NoVerticalClassMerging
  interface ImplementedByClass {

    void m();
  }

  interface ImplementedByLambda {

    void m();
  }

  static class A implements ImplementedByClass {

    @Override
    public void m() {
      System.out.print("H");
      System.out.print("e");
      System.out.print("l");
      System.out.print("l");
      System.out.println("o");
    }
  }
}
