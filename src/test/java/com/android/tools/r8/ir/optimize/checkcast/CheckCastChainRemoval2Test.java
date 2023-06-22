// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.checkcast;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckCastChainRemoval2Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  public CheckCastChainRemoval2Test() {}

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testCheckCast() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, Main.class)
        .addKeepAllClassesRule()
        .addKeepMainRule(Main.class)
        .addOptionsModification((opt) -> opt.testing.enableCheckCastAndInstanceOfRemoval = false)
        .enableInliningAnnotations()
        .setMinApi(this.parameters)
        .run(this.parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false");
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(check());
    }

    @NeverInline
    private static boolean check() {
      Object o = null;
      A a = (A) o;
      if (a instanceof B) {
        B b = (B) a;
        return true;
      } else {
        return false;
      }
    }
  }

  static class B extends A {}

  static class A {}
}
