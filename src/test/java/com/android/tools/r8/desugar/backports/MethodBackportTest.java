// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class MethodBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MethodBackportTest(TestParameters parameters) {
    super(parameters, String.class, Main.class);
    registerTarget(AndroidApiLevel.O, 0);
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) throws NoSuchMethodException {
      assertEquals(0, Main.class.getMethod("empty").getParameterCount());
      assertEquals(
          2, Main.class.getMethod("wideArgs", long.class, double.class).getParameterCount());
      assertEquals(1, Main.class.getMethod("args", Object[].class).getParameterCount());
    }

    public static void empty() {}

    public static void wideArgs(long l, double d) {}

    public static void args(Object... o) {}
  }
}
