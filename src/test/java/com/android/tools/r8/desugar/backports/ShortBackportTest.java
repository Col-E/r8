// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ShortBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public ShortBackportTest(TestParameters parameters) {
    super(parameters, Short.class, Main.class);
    registerTarget(AndroidApiLevel.O, 16);
    registerTarget(AndroidApiLevel.N, 8);
    registerTarget(AndroidApiLevel.K, 7);
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testHashCode();
      testCompare();
      testToUnsignedInt();
      testToUnsignedLong();
    }

    private static void testHashCode() {
      for (int i = Short.MIN_VALUE; i < Short.MAX_VALUE; i++) {
        assertEquals(i, Short.hashCode((short) i));
      }
    }

    private static void testCompare() {
      assertTrue(Short.compare((short) 1, (short) 0) > 0);
      assertTrue(Short.compare((short) 0, (short) 0) == 0);
      assertTrue(Short.compare((short) 0, (short) 1) < 0);
      assertTrue(Short.compare(Short.MIN_VALUE, Short.MAX_VALUE) < 0);
      assertTrue(Short.compare(Short.MAX_VALUE, Short.MIN_VALUE) > 0);
      assertTrue(Short.compare(Short.MIN_VALUE, Short.MIN_VALUE) == 0);
      assertTrue(Short.compare(Short.MAX_VALUE, Short.MAX_VALUE) == 0);
    }

    private static void testToUnsignedInt() {
      assertEquals(0, Short.toUnsignedInt((short) 0));
      assertEquals(32767, Short.toUnsignedInt(Short.MAX_VALUE));
      assertEquals(32768, Short.toUnsignedInt(Short.MIN_VALUE));
      assertEquals(65535, Short.toUnsignedInt((short) -1));
    }

    private static void testToUnsignedLong() {
      assertEquals(0L, Short.toUnsignedLong((short) 0));
      assertEquals(32767L, Short.toUnsignedLong(Short.MAX_VALUE));
      assertEquals(32768L, Short.toUnsignedLong(Short.MIN_VALUE));
      assertEquals(65535L, Short.toUnsignedLong((short) -1));
    }
  }
}
