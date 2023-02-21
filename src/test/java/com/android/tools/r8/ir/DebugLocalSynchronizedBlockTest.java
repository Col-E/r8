// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DebugLocalSynchronizedBlockTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public DebugLocalSynchronizedBlockTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  public static class Main {

    private int state = 0;
    private int[] values = {42};

    public static void main(String[] args) {
      Main main = new Main();
      if (args.length == 0) {
        main.state = 1;
      } else {
        main.state = 0;
      }
      main.thread1();
      System.out.println("Hello World!");
    }

    void thread1() {
      int s;
      do {
        synchronized (Main.class) {
          s = state;
        }
      } while (s != 1); // Busy loop.
      synchronized (Main.class) {
        values = null;
        state = 2;
      }
    }
  }
}
