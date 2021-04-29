// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.callsites;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CallSiteOptimizationLambdaPropagationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public CallSiteOptimizationLambdaPropagationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(CallSiteOptimizationLambdaPropagationTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/186729231): Should succeed with "A", "B".
        .assertSuccessWithOutputLines("A", parameters.isCfRuntime() ? "A" : "B");
  }

  static class TestClass {

    public static void main(String[] args) {
      add(new A());
      Consumer consumer = TestClass::add;
      consumer.accept("B");
    }

    // TODO(b/186729231): Incorrectly fail to propagate "B" as an argument to add().
    @NeverInline
    static void add(Object o) {
      System.out.println(o.toString());
    }
  }

  interface Consumer {
    void accept(Object o);
  }

  @NeverClassInline
  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }
}
