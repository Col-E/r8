// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.regalloc;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B136717060 extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForD8()
        .addInnerClasses(B136717060.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  static class TestClass {

    public static void main(String[] args) {
      test();
    }

    static void test() {
      method(3L, 3L, 1L, 0L);
      method(Long.MAX_VALUE, 1, Long.MAX_VALUE, 0L);
      method(Long.MIN_VALUE, 3L, Long.MIN_VALUE / 3L - 1L, 1L);
      method(Long.MIN_VALUE + 1, -1, Long.MAX_VALUE, 0L);
      method(Long.MIN_VALUE, -1, Long.MIN_VALUE, 0L);
    }

    static void method(long x, long y, Object a, Object b) {}
  }
}
