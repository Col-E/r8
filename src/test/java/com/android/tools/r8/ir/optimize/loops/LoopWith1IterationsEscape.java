// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.loops;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LoopWith1IterationsEscape extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LoopWith1IterationsEscape(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testLoopRemoved() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addDontObfuscate()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("end 0", "iteration", "end 1");
  }

  public static class Main {

    public static void main(String[] args) {
      loopExit();
      loopNoExit();
    }

    @NeverInline
    public static void loopNoExit() {
      Object[] objects = new Object[1];
      int i;
      for (i = 0; i < objects.length; i++) {
        if (System.currentTimeMillis() < 0) {
          break;
        }
        System.out.println("iteration");
      }
      System.out.println("end " + i);
    }

    @NeverInline
    public static void loopExit() {
      Object[] objects = new Object[1];
      int i;
      for (i = 0; i < objects.length; i++) {
        if (System.currentTimeMillis() > 0) {
          break;
        }
        System.out.println("iteration");
      }
      System.out.println("end " + i);
    }
  }
}
