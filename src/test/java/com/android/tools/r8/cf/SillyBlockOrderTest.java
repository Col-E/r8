// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SillyBlockOrderTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("true");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public SillyBlockOrderTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED)
          .inspect(i -> checkNumberOfGotos(i, 2));
    } else {
      testForD8()
          .addProgramClasses(TestClass.class)
          .setMinApi(parameters)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED)
          .inspect(i -> checkNumberOfGotos(i, 1));
    }
  }

  private void checkNumberOfGotos(CodeInspector inspector, int expected) {
    try {
      MethodSubject method =
          inspector.method(TestClass.class.getMethod("doubleConditional", boolean.class));
      assertEquals(
          expected, method.streamInstructions().filter(InstructionSubject::isGoto).count());
    } catch (NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  public static class TestClass {

    public static int returnArg(int value) {
      return value;
    }

    public static boolean doubleConditional(boolean arg) {
      // When generating code for this we would like to check that the block order used ensures that
      // the final conditional jumps to the return and avoids a goto in the fallthrough position.
      // Having the goto is not incorrect, just suboptimal.
      // The art test 458-checker-instruct-simplification has hard coded this expectation too.
      return (arg ? returnArg(0) : returnArg(1)) != 2;
    }

    public static void main(String[] args) {
      System.out.println(doubleConditional(args.length == 0));
    }
  }
}
