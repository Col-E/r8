// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class DoubleBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public DoubleBackportTest(TestParameters parameters) {
    super(parameters, Double.class, Main.class);
    registerTarget(AndroidApiLevel.N, 10);
  }

  static final class Main extends MiniAssert {
    private static final double[] interestingValues = {
        Double.MIN_VALUE, Double.MAX_VALUE,
        Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
        Integer.MIN_VALUE, Integer.MAX_VALUE,
        Long.MIN_VALUE, Long.MAX_VALUE,
        Double.NaN,
        0d,
        -1d, 1d,
        -0.5d, 0.5d,
        -0.1d, 0.1d,
    };

    public static void main(String[] args) {
      for (double value : interestingValues) {
        assertEquals(expectedHashCode(value), Double.hashCode(value));
      }

      assertTrue(Double.isFinite(0d));
      assertTrue(Double.isFinite(Double.MIN_VALUE));
      assertTrue(Double.isFinite(Double.MAX_VALUE));
      assertFalse(Double.isFinite(Double.NaN));
      assertFalse(Double.isFinite(Double.POSITIVE_INFINITY));
      assertFalse(Double.isFinite(Double.NEGATIVE_INFINITY));

      for (double x : interestingValues) {
        for (double y : interestingValues) {
          assertEquals(Math.max(x, y), Double.max(x, y));
        }
      }

      for (double x : interestingValues) {
        for (double y : interestingValues) {
          assertEquals(Math.min(x, y), Double.min(x, y));
        }
      }

      for (double x : interestingValues) {
        for (double y : interestingValues) {
          assertEquals(x + y, Double.sum(x, y));
        }
      }
    }

    @NeverInline // Avoid changing invoke counts in main().
    private static int expectedHashCode(double d) {
      return Double.valueOf(d).hashCode();
    }
  }
}
