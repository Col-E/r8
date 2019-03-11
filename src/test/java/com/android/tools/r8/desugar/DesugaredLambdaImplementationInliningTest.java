// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import org.junit.Test;

// Test if the static method of the lambda implementation (SINGLE_CALLER) is inlined into the
// interface method of the lambda class.
public class DesugaredLambdaImplementationInliningTest extends TestBase {
  @Test
  public void test() throws Exception {
    class Counter {
      private int i = 0;
    }
    Counter counter = new Counter();
    R8TestRunResult result =
        testForR8Compat(Backend.DEX)
            .addInnerClasses(DesugaredLambdaImplementationInliningTest.class)
            .addKeepMainRule(DesugaredLambdaImplementationInliningTest.TestClass.class)
            .noMinification()
            .run(TestClass.class)
            .assertSuccess()
            .inspect(
                inspector -> {
                  inspector
                      .clazz(DesugaredLambdaImplementationInliningTest.TestClass.class)
                      .forAllMethods(
                          fms -> {
                            if (fms.isStatic() && !fms.getOriginalName().equals("main")) {
                              ++counter.i;
                            }
                          });
                });

    // TODO(b/126323172) Change expected value to zero after fixed.
    assertEquals(2, counter.i);
  }

  static class TestClass {
    public static void main(String[] args) {
      Runnable runnable = () -> System.out.println("Running desugared stateless lambda.");
      runnable.run();

      String s = "lambda-state";
      runnable = () -> System.out.println("Running desugared stateful lambda: " + s + ".");
      runnable.run();
    }
  }
}
