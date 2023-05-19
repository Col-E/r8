// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.NeverClassInline;
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
public class NonFinalFieldWithNonDefaultValueAssignmentPropagationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NonFinalFieldWithNonDefaultValueAssignmentPropagationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NonFinalFieldWithNonDefaultValueAssignmentPropagationTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());
    // TODO(b/147799637): Should be absent.
    assertThat(testClassSubject.uniqueMethodWithOriginalName("dead"), isPresent());

    ClassSubject configClassSubject = inspector.clazz(Config.class);
    assertThat(configClassSubject, isPresent());

    MethodSubject configConstructorSubject = configClassSubject.init();
    assertThat(configConstructorSubject, isPresent());
    // TODO(b/147799637): Should be true.
    assertFalse(
        configConstructorSubject.streamInstructions().noneMatch(InstructionSubject::isInstancePut));
  }

  static class TestClass {

    public static void main(String[] args) {
      if (!new Config().alwaysTrue) {
        dead();
      }
    }

    @NeverInline
    static void dead() {
      System.out.println("Dead!");
    }
  }

  @NeverClassInline
  static class Config {

    boolean alwaysTrue;

    @NeverInline
    Config() {
      // An instruction that cannot read alwaysTrue, because the receiver has not escaped
      // (except into Object.<init>()).
      System.out.println("Hello world!");
      // Since the receiver has not escaped, we are guaranteed that all reads will evaluate to true.
      alwaysTrue = true;
    }
  }
}
