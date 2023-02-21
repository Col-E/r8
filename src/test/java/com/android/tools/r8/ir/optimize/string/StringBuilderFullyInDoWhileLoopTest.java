// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringBuilderFullyInDoWhileLoopTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StringBuilderFullyInDoWhileLoopTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(StringBuilderFullyInDoWhileLoopTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("000");
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject mainMethodSubject = inspector.clazz(TestClass.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertTrue(
        mainMethodSubject
            .streamInstructions()
            .noneMatch(instruction -> instruction.isNewInstance("java.lang.StringBuilder")));
  }

  static class TestClass {
    public static void main(String[] args) {
      int i = 0;
      do {
        StringBuilder builder = new StringBuilder();
        builder.append("0");
        System.out.print(builder.toString());
        i++;
      } while (i < 3);
      System.out.println();
    }
  }
}
