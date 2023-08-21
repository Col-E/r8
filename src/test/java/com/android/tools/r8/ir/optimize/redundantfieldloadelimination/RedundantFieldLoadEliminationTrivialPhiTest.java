// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RedundantFieldLoadEliminationTrivialPhiTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public RedundantFieldLoadEliminationTrivialPhiTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(RedundantFieldLoadEliminationTrivialPhiTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("1", "1");
  }

  static class A {
    int x;
    int y;
  }

  static class TestClass {

    public static void main(String[] args) {
      int unknown = System.currentTimeMillis() > 0 ? 1 : 2;
      A a = new A();
      boolean b = true;
      while (b) {
        // Ensure we have a phi of a value that will become trivial once fields are optimized.
        int copy = unknown;
        a.x = unknown;
        a.y = copy;
        unknown = a.x; // Replacing this field read will make the phi trivial.
        int y = a.y; // This read will be using the phi that becomes trivial.
        System.out.println(y);
        System.out.println(copy);
        b = false;
      }
    }
  }
}
