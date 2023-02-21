// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassInlinerWithSimpleSuperTypeTest extends TestBase {

  private final boolean enableClassInlining;
  private final TestParameters parameters;

  @Parameters(name = "{1}, enable class inlining: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public ClassInlinerWithSimpleSuperTypeTest(
      boolean enableClassInlining, TestParameters parameters) {
    this.enableClassInlining = enableClassInlining;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlinerWithSimpleSuperTypeTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.enableClassInlining = enableClassInlining)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyCandidateIsClassInlined)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void verifyCandidateIsClassInlined(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    MethodSubject mainMethodSubject = testClassSubject.mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertNotEquals(
        enableClassInlining,
        mainMethodSubject.streamInstructions().anyMatch(InstructionSubject::isNewInstance));

    ClassSubject aClassSubject = inspector.clazz(A.class);
    assertNotEquals(enableClassInlining, aClassSubject.isPresent());

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertNotEquals(enableClassInlining, bClassSubject.isPresent());

    ClassSubject cClassSubject = inspector.clazz(C.class);
    assertNotEquals(enableClassInlining, cClassSubject.isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      new C("Hello world!").method();
    }
  }

  @NoVerticalClassMerging
  static class A {}

  @NoVerticalClassMerging
  static class B extends A {}

  static class C extends B {

    String greeting;

    C(String greeting) {
      if (greeting == null) {
        throw new RuntimeException();
      }
      this.greeting = greeting;
    }

    @NeverInline
    void method() {
      System.out.println(greeting);
    }
  }
}
