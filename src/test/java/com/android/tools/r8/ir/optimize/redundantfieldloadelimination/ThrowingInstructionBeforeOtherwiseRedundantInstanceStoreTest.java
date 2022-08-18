// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ThrowingInstructionBeforeOtherwiseRedundantInstanceStoreTest extends TestBase {

  @Parameter(0)
  public CompilationMode mode;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, mode: {0}")
  public static List<Object[]> parameters() {
    return buildParameters(
        CompilationMode.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForD8()
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1");
  }

  static class Main {

    int sum = 0;

    public static void main(String[] args) {
      Main main = new Main();
      try {
        main.test(new int[] {1});
        throw new RuntimeException();
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println(main.sum);
      }
    }

    void test(int[] array) {
      sum += array[0];
      sum += array[1];
    }
  }
}
