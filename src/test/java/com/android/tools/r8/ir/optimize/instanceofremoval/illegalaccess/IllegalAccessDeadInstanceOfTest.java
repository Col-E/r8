// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.instanceofremoval.illegalaccess;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.instanceofremoval.illegalaccess.pkg.A;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IllegalAccessDeadInstanceOfTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  public IllegalAccessDeadInstanceOfTest() {}

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getTransformedA())
        .run(this.parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getTransformedA())
        .addKeepMainRule(Main.class)
        .setMinApi(this.parameters)
        .run(this.parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false");
  }

  private static byte[] getTransformedA() throws IOException {
    return transformer(A.class).setClassAccessFlags(0).transform();
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(check());
    }

    private static boolean check() {
      Object o = null;
      // A is transformed to be a non-public class in a different package.
      if (o instanceof A) {
        return true;
      }
      return false;
    }
  }
}
