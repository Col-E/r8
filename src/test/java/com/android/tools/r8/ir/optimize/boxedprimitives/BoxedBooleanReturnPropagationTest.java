// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.boxedprimitives;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BoxedBooleanReturnPropagationTest extends TestBase {

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
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertTrue(
                  mainMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstNumber(0)));
              assertTrue(
                  mainMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstNumber(1)));

              MethodSubject nullTest =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("nullTest");
              assertThat(nullTest, isPresent());
              assertTrue(nullTest.streamInstructions().noneMatch(InstructionSubject::isIf));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false", "true", "notnull", "notnull");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(getFalse().booleanValue());
      System.out.println(getTrue().booleanValue());
      nullTest();
    }

    @NeverInline
    static void nullTest() {
      if (getFalse() == null) {
        System.out.println("null");
      } else {
        System.out.println("notnull");
      }
      if (getTrue() == null) {
        System.out.println("null");
      } else {
        System.out.println("notnull");
      }
    }

    @NeverInline
    static Boolean getFalse() {
      return false;
    }

    @NeverInline
    static Boolean getTrue() {
      return true;
    }
  }
}
