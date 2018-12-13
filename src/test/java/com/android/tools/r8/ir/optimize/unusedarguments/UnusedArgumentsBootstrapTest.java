// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import com.android.tools.r8.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedArgumentsBootstrapTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public UnusedArgumentsBootstrapTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    testForR8(backend)
        .addInnerClasses(UnusedArgumentsBootstrapTest.class)
        .addKeepMainRule(TestClass.class)
        .run(TestClass.class)
        .assertSuccessWithOutput("Hello");
  }

  static class TestClass {

    public static void main(String[] args) {
      test((str) -> System.out.print("Hello"));
    }

    public static void test(Consumer<String> consumer) {
      consumer.accept(null);
    }
  }

  interface Consumer<T> {

    void accept(T obj);
  }
}
