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
public final class CharacterBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public CharacterBackportTest(TestParameters parameters) {
    super(parameters, Character.class, Main.class);
    registerTarget(AndroidApiLevel.N, 8);
    registerTarget(AndroidApiLevel.K, 7);
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testHashCode();
      testCompare();
    }

    private static void testHashCode() {
      for (int i = Character.MIN_VALUE; i < Character.MAX_VALUE; i++) {
        assertEquals(i, Character.hashCode((char) i));
      }
    }

    private static void testCompare() {
      assertTrue(Character.compare('b', 'a') > 0);
      assertTrue(Character.compare('a', 'a') == 0);
      assertTrue(Character.compare('a', 'b') < 0);
      assertTrue(Character.compare(Character.MIN_VALUE, Character.MAX_VALUE) < 0);
      assertTrue(Character.compare(Character.MAX_VALUE, Character.MIN_VALUE) > 0);
      assertTrue(Character.compare(Character.MIN_VALUE, Character.MIN_VALUE) == 0);
      assertTrue(Character.compare(Character.MAX_VALUE, Character.MAX_VALUE) == 0);
    }
  }
}
