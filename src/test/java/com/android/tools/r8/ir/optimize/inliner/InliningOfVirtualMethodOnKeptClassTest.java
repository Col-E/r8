// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InliningOfVirtualMethodOnKeptClassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InliningOfVirtualMethodOnKeptClassTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(
            "-keep class " + A.class.getTypeName() + " { void bar(); }",
            "-keep class " + I.class.getTypeName() + " { void baz(); }")
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyOutput)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("foo", "bar", "baz");
  }

  private void verifyOutput(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("foo"), not(isPresent()));
    assertThat(classSubject.uniqueMethodWithOriginalName("bar"), isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("baz"), isPresent());
  }

  @NeverClassInline
  static class TestClass extends A implements I {

    public static void main(String[] args) {
      TestClass instance = new TestClass();
      instance.foo();
      instance.bar();
      instance.baz();
    }

    public void foo() {
      System.out.println("foo");
    }

    @Override
    public void bar() {
      System.out.println("bar");
    }

    @Override
    public void baz() {
      System.out.println("baz");
    }
  }

  abstract static class A {

    abstract void bar();
  }

  interface I {

    void baz();
  }
}
