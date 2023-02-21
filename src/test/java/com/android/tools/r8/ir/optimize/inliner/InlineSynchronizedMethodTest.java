// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InlineSynchronizedMethodTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public InlineSynchronizedMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InlineSynchronizedMethodTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(
            inspector -> {
              // TODO(b/163007704): Identify and remove trivial/unobservable monitor enter/exit
              // pairs.
              assertEquals(
                  2,
                  inspector
                      .clazz(TestClass.class)
                      .uniqueMethodWithOriginalName("main")
                      .streamInstructions()
                      .filter(i -> i.isMonitorEnter() || i.isMonitorExit())
                      .count());
            });
  }

  static class TestClass {

    public static synchronized String foo(String msg) {
      // No throwing instructions, so no need to join exceptions on exit.
      return msg;
    }

    public static void main(String[] args) {
      System.out.println(foo(args.length == 1 ? args[0] : "Hello, world"));
    }
  }
}
