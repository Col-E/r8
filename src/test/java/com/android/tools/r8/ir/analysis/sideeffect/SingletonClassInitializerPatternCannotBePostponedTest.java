// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

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
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingletonClassInitializerPatternCannotBePostponedTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SingletonClassInitializerPatternCannotBePostponedTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(SingletonClassInitializerPatternCannotBePostponedTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(A.class);
    assertThat(classSubject, isPresent());

    // The field A.INSTANCE has been accessed to allow inlining of A.inlineable().
    assertThat(classSubject.uniqueFieldWithOriginalName("INSTANCE"), isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("inlineable"), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      A.inlineable();
      System.out.println(A.getInstance().getMessage());
    }
  }

  static class A {

    static B INSTANCE = new B("world!");

    static void inlineable() {
      System.out.print(" ");
    }

    static B getInstance() {
      return INSTANCE;
    }
  }

  static class B {

    static {
      System.out.print("Hello");
    }

    final String message;

    B(String message) {
      this.message = message;
    }

    String getMessage() {
      return message;
    }
  }
}
