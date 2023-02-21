// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.staticinterfacemethod;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NoMissingClassWarningForNonInterfaceInvoke extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public NoMissingClassWarningForNonInterfaceInvoke(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForD8(Backend.CF)
        .addProgramClasses(TestClass.class)
        .setMinApi(parameters)
        .setIntermediate(true)
        .compile()
        .assertNoWarningMessages();
  }

  static class MissingClass {
    static void test() {}
  }

  static class TestClass {
    public static void main(String[] args) {
      MissingClass.test();
    }
  }
}
