// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

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
public class FinalFieldWithDefaultValueAssignmentPropagationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public FinalFieldWithDefaultValueAssignmentPropagationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(FinalFieldWithDefaultValueAssignmentPropagationTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("false");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());
    assertThat(testClassSubject.uniqueMethodWithOriginalName("dead"), not(isPresent()));

    ClassSubject configClassSubject = inspector.clazz(Config.class);
    assertThat(configClassSubject, isPresent());

    MethodSubject configConstructorSubject = configClassSubject.init();
    assertThat(configConstructorSubject, isPresent());
    assertTrue(
        configConstructorSubject.streamInstructions().noneMatch(InstructionSubject::isInstancePut));
  }

  static class TestClass {

    public static void main(String[] args) {
      Config nullableConfig = System.currentTimeMillis() >= 0 ? new Config() : null;
      if (nullableConfig.alwaysFalse) {
        dead();
      }
    }

    @NeverInline
    static void dead() {
      System.out.println("Dead!");
    }
  }

  static class Config {

    final boolean alwaysFalse;

    @NeverInline
    Config() {
      // An instruction that causes alwaysFalse to be read.
      System.out.println(this);
      // Since alwaysFalse is final we can remove the assignment, even if the receiver escapes.
      alwaysFalse = false;
    }

    @Override
    public String toString() {
      return Boolean.toString(alwaysFalse);
    }
  }
}
