// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StatefulSingletonClassInliningTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StatefulSingletonClassInliningTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(StatefulSingletonClassInliningTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true");
  }

  static class TestClass {

    public static void main(String[] args) {
      State.get().set();
      State.get().print();
    }
  }

  static class State {

    static StatefulSingleton SINGLETON = new StatefulSingleton();

    static StatefulSingleton get() {
      return SINGLETON;
    }
  }

  static class StatefulSingleton {

    boolean field;

    @NeverInline
    void set() {
      field = true;
    }

    void print() {
      System.out.println(field);
    }
  }
}
