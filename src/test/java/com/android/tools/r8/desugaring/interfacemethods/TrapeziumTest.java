// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TrapeziumTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public TrapeziumTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testTrapezium() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(TrapeziumTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(StringUtils.lines("foo from superI", "foo from I"));
  }

  static class Main {

    public static void main(String[] args) {
      new SuperA().foo();
      new A().foo();
    }
  }

  static class SuperA implements SuperI {}

  static class A extends SuperA implements I {}

  interface I extends SuperI {
    default void foo() {
      System.out.println("foo from I");
    }
  }

  interface SuperI {
    default void foo() {
      System.out.println("foo from superI");
    }
  }
}
