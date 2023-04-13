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
public final class BooleanBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public BooleanBackportTest(TestParameters parameters) {
    super(parameters, Boolean.class, Main.class);
    registerTarget(AndroidApiLevel.N, 20);
    registerTarget(AndroidApiLevel.K, 4);
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testHashCode();
      testCompare();
      testLogicalAnd();
      testLogicalOr();
      testLogicalXor();
    }

    private static void testHashCode() {
      assertEquals(1231, Boolean.hashCode(true));
      assertEquals(1237, Boolean.hashCode(false));
    }

    private static void testCompare() {
      assertTrue(Boolean.compare(true, false) > 0);
      assertTrue(Boolean.compare(true, true) == 0);
      assertTrue(Boolean.compare(false, false) == 0);
      assertTrue(Boolean.compare(false, true) < 0);
    }

    private static void testLogicalAnd() {
      assertTrue(Boolean.logicalAnd(true, true));
      assertFalse(Boolean.logicalAnd(true, false));
      assertFalse(Boolean.logicalAnd(false, true));
      assertFalse(Boolean.logicalAnd(false, false));

      // Ensure optimization does not short-circuit path to second boolean.
      sideEffectCount = 0;
      assertFalse(Boolean.logicalAnd(sideEffectFalse(), sideEffectFalse()));
      assertEquals(2, sideEffectCount);
    }

    private static void testLogicalOr() {
      assertTrue(Boolean.logicalOr(true, true));
      assertTrue(Boolean.logicalOr(true, false));
      assertTrue(Boolean.logicalOr(false, true));
      assertFalse(Boolean.logicalOr(false, false));

      // Ensure optimization does not short-circuit path to second boolean.
      sideEffectCount = 0;
      assertTrue(Boolean.logicalOr(sideEffectTrue(), sideEffectTrue()));
      assertEquals(2, sideEffectCount);
    }

    private static void testLogicalXor() {
      assertFalse(Boolean.logicalXor(true, true));
      assertTrue(Boolean.logicalXor(true, false));
      assertTrue(Boolean.logicalXor(false, true));
      assertFalse(Boolean.logicalXor(false, false));
    }

    private static int sideEffectCount;
    private static boolean sideEffectTrue() {
      sideEffectCount++;
      return true;
    }
    private static boolean sideEffectFalse() {
      sideEffectCount++;
      return false;
    }
  }
}
