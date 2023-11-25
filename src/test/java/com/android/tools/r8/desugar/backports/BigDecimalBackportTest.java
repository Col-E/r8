// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.math.BigDecimal;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class BigDecimalBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public BigDecimalBackportTest(TestParameters parameters) {
    super(parameters, BigDecimal.class, Main.class);
    registerTarget(AndroidApiLevel.Q, 3);
    ignoreInvokes("valueOf");
    ignoreInvokes("toString");
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testStripTrailingZeros();
    }

    private static void testStripTrailingZeros() {
      assertEquals("0", BigDecimal.valueOf(0.0).stripTrailingZeros().toString());
      assertEquals("1", BigDecimal.valueOf(1.0).stripTrailingZeros().toString());
      assertEquals("-1", BigDecimal.valueOf(-1.0).stripTrailingZeros().toString());
    }
  }
}
