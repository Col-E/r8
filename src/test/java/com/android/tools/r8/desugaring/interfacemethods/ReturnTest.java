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
public class ReturnTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public ReturnTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReturn() throws Exception {
    testForDesugaring(parameters)
        .addInnerClasses(ReturnTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(
            StringUtils.lines(
                "com.android.tools.r8.desugaring.interfacemethods.ReturnTest$SuperA",
                "com.android.tools.r8.desugaring.interfacemethods.ReturnTest$A"));
  }

  static class Main {

    public static void main(String[] args) {
      new SuperA().print();
      new A().print();
    }
  }

  static class SuperA implements SuperI {
    public void print() {
      SuperA a = get();
      System.out.println(a.getClass().getName());
    }
  }

  static class A extends SuperA implements I {
    public void print() {
      A a = get();
      System.out.println(a.getClass().getName());
    }
  }

  interface I extends SuperI {
    default A get() {
      return new A();
    }
  }

  interface SuperI {
    default SuperA get() {
      return new SuperA();
    }
  }
}
