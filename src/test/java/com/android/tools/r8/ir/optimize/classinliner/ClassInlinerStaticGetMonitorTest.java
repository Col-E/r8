// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This is a reproduction of b/147411673 where we inline classes and remove monitor instructions.
 */
@RunWith(Parameterized.class)
public class ClassInlinerStaticGetMonitorTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerStaticGetMonitorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlinerStaticGetMonitorTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .addDontObfuscate()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("20000");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(
        inspector.clazz(Container.class).uniqueMethodWithOriginalName("increment"), isPresent());
  }

  static class TestClass {

    private static volatile Thread t1 = new Thread(TestClass::produce1);
    private static volatile Thread t2 = new Thread(TestClass::produce2);

    @NeverInline
    static void produce1() {
      for (int i = 0; i < 10000; i++) {
        Container.getInstance().increment();
      }
    }

    @NeverInline
    static void produce2() {
      for (int i = 0; i < 10000; i++) {
        Container.getInstance().increment();
      }
    }

    public static void main(String[] args) {
      t1.start();
      t2.start();
      while (t1.isAlive() || t2.isAlive()) {}
      System.out.println(Container.counter);
    }
  }

  static class Container {

    static Container INSTANCE = new Container();
    public static int counter = 0;

    static Container getInstance() {
      return INSTANCE;
    }

    @NeverInline
    final void increment() {
      synchronized (this) {
        counter += 1;
      }
    }
  }
}
