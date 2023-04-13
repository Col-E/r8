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
public final class ByteBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public ByteBackportTest(TestParameters parameters) {
    super(parameters, Byte.class, Main.class);
    registerTarget(AndroidApiLevel.O, 17);
    registerTarget(AndroidApiLevel.N, 9);
    registerTarget(AndroidApiLevel.K, 7);
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testHashCode();
      testCompare();
      testToUnsignedInt();
      testToUnsignedLong();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void testHashCode() {
      for (int i = Byte.MIN_VALUE; i < Byte.MAX_VALUE; i++) {
        assertEquals(i, Byte.hashCode((byte) i));
      }
      // Test unused invoke.
      Byte.hashCode((byte) 1);
    }

    private static void testCompare() {
      assertTrue(Byte.compare((byte) 1, (byte) 0) > 0);
      assertTrue(Byte.compare((byte) 0, (byte) 0) == 0);
      assertTrue(Byte.compare((byte) 0, (byte) 1) < 0);
      assertTrue(Byte.compare(Byte.MIN_VALUE, Byte.MAX_VALUE) < 0);
      assertTrue(Byte.compare(Byte.MAX_VALUE, Byte.MIN_VALUE) > 0);
      assertTrue(Byte.compare(Byte.MIN_VALUE, Byte.MIN_VALUE) == 0);
      assertTrue(Byte.compare(Byte.MAX_VALUE, Byte.MAX_VALUE) == 0);
    }

    private static void testToUnsignedInt() {
      assertEquals(0, Byte.toUnsignedInt((byte) 0));
      assertEquals(127, Byte.toUnsignedInt(Byte.MAX_VALUE));
      assertEquals(128, Byte.toUnsignedInt(Byte.MIN_VALUE));
      assertEquals(255, Byte.toUnsignedInt((byte) -1));
    }

    private static void testToUnsignedLong() {
      assertEquals(0L, Byte.toUnsignedLong((byte) 0));
      assertEquals(127L, Byte.toUnsignedLong(Byte.MAX_VALUE));
      assertEquals(128L, Byte.toUnsignedLong(Byte.MIN_VALUE));
      assertEquals(255L, Byte.toUnsignedLong((byte) -1));
    }
  }
}
