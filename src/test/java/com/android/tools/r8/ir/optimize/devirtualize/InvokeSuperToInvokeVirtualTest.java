// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.devirtualize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
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
public class InvokeSuperToInvokeVirtualTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSuperToInvokeVirtualTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeSuperToInvokeVirtualTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    MethodSubject negativeTestSubject = bClassSubject.uniqueMethodWithOriginalName("negativeTest");
    assertThat(negativeTestSubject, isPresent());
    assertTrue(negativeTestSubject.streamInstructions().anyMatch(this::isInvokeSuper));
    assertTrue(
        negativeTestSubject.streamInstructions().noneMatch(InstructionSubject::isInvokeVirtual));

    // B.positiveTest() is moved to A as a result of bridge hoisting.
    MethodSubject positiveTestSubject = aClassSubject.uniqueMethodWithOriginalName("positiveTest");
    assertThat(positiveTestSubject, isPresent());
    assertTrue(positiveTestSubject.streamInstructions().noneMatch(this::isInvokeSuper));
    assertTrue(
        positiveTestSubject.streamInstructions().anyMatch(InstructionSubject::isInvokeVirtual));
  }

  private boolean isInvokeSuper(InstructionSubject instruction) {
    if (parameters.isCfRuntime()) {
      return instruction.asCfInstruction().isInvokeSpecial();
    } else {
      return instruction.asDexInstruction().isInvokeSuper();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new B().negativeTest();
      new B().positiveTest();

      if (System.currentTimeMillis() < 0) {
        // To keep B.hello().
        new B().hello();
      }
    }
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    void hello() {
      System.out.print("Hello");
    }

    @NeverInline
    @NoMethodStaticizing
    void world() {
      System.out.println(" world!");
    }
  }

  @NeverClassInline
  static class B extends A {

    @Override
    void hello() {
      System.out.println("Unexpected!");
    }

    @NeverInline
    void negativeTest() {
      super.hello();
    }

    @NeverInline
    void positiveTest() {
      super.world();
    }
  }
}
