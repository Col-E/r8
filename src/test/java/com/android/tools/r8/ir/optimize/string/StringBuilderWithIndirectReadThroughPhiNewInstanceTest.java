// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.string.utils.StringBuilderCodeMatchers.isInvokeStringBuilderAppendWithString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringBuilderWithIndirectReadThroughPhiNewInstanceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
              assertEquals(
                  2,
                  mainMethodSubject
                      .streamInstructions()
                      .filter(isInvokeStringBuilderAppendWithString())
                      .count());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foobar", "foo");
  }

  static class Main {

    public static void main(String[] args) {
      boolean alwaysTrue = System.currentTimeMillis() > 0;
      StringBuilder sb0 = new StringBuilder();
      StringBuilder sb1 = alwaysTrue ? sb0 : new StringBuilder();
      sb0.append("foo");
      StringBuilder sb2 = new StringBuilder(sb1);
      sb0.append("bar");
      System.out.println(sb0.toString());
      System.out.println(sb2.toString());
    }
  }
}
