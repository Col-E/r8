// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NoLongerSatisfiedIfRuleTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NoLongerSatisfiedIfRuleTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(NoLongerSatisfiedIfRuleTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-if class " + A.class.getTypeName(),
            "-keep class " + B.class.getTypeName() + " { void m(); }")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("B");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(A.class), not(isPresent()));

    ClassSubject bClassSubject = inspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());
    assertThat(bClassSubject.uniqueMethodWithOriginalName("m"), isAbsent());
  }

  static class TestClass {

    static boolean alwaysFalse = false;

    public static void main(String[] args) {
      if (alwaysFalse) {
        System.out.println(new A());
      }
      System.out.println(new B());
    }
  }

  static class A {}

  static class B {

    void m() {}

    @Override
    public String toString() {
      return "B";
    }
  }
}
