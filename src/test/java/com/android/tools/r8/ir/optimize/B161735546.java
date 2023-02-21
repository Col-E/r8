// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B161735546 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public B161735546(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(B161735546.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("1", "2", "3");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(B161735546.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("1", "2", "3");
  }

  static class TestClass {
    public static void main(String[] args) {
      new CounterUtils() {}.add(new Counter(), 3);
    }
  }

  static class Counter {
    int i = 0;
  }

  interface CounterConsumer {
    void accept(Counter counter);
  }

  interface CounterUtils {
    default void add(Counter counter, int i) {
      if (i == 0) {
        return;
      }
      CounterConsumer continuation =
          c -> {
            System.out.println(++counter.i);
            add(counter, i - 1);
          };
      continuation.accept(counter);
    }
  }
}
