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
public class FieldReadBeforeOtherwiseRedundantStoreTest extends TestBase {

  @Parameter(0)
  public CompilationMode mode;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, mode: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        CompilationMode.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForD8()
        .addInnerClasses(FieldReadBeforeOtherwiseRedundantStoreTest.class)
        .setMinApi(parameters)
        .setMode(mode)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }

  static class Main {

    public static int field = 0;

    public int testLoop() {
      int iterations = 50;
      for (int i = 0; i < iterations; i++) {
        int a = field;
        field = a + i;
        int b = field;
        field = b + 2 * i;
      }
      assertIntEquals(field, 3675);
      return field;
    }

    public static void main(String[] args) {
      Main obj = new Main();
      obj.testLoop();
    }

    public static void assertIntEquals(int expected, int result) {
      if (expected != result) {
        throw new Error("Expected: " + expected + ", found: " + result);
      }
    }
  }
}
