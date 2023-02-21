// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.classmerging.vertical.testclasses.NonReboundFieldAccessWithMergedTypeTestClasses;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NonReboundFieldAccessWithMergedTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NonReboundFieldAccessWithMergedTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addInnerClasses(NonReboundFieldAccessWithMergedTypeTestClasses.class)
        .addKeepMainRule(Main.class)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(GreetingBase.class))
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new C(new Greeting()).greeting);
    }
  }

  @NeverClassInline
  static class C extends NonReboundFieldAccessWithMergedTypeTestClasses.B {

    public C(GreetingBase greeting) {
      super(greeting);
    }
  }

  public static class GreetingBase {}

  public static class Greeting extends GreetingBase {

    @Override
    public String toString() {
      return "Hello world!";
    }
  }
}
