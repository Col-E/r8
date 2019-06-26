// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static java.lang.Integer.signum;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class CharacterBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public CharacterBackportTest(TestParameters parameters) {
    super(parameters, Character.class, Main.class);
    registerTarget(AndroidApiLevel.N, 8);
    registerTarget(AndroidApiLevel.K, 7);
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      for (int i = Character.MIN_VALUE; i < Character.MAX_VALUE; i++) {
        assertEquals(i, Character.hashCode((char) i));
      }

      // signum() normalizes result to [-1, 1] since the values differ across VMs but signs match.
      assertEquals(1, signum(Character.compare('b', 'a')));
      assertEquals(0, signum(Character.compare('a', 'a')));
      assertEquals(-1, signum(Character.compare('a', 'b')));
      assertEquals(-1, signum(Character.compare(Character.MIN_VALUE, Character.MAX_VALUE)));
      assertEquals(1, signum(Character.compare(Character.MAX_VALUE, Character.MIN_VALUE)));
      assertEquals(0, signum(Character.compare(Character.MIN_VALUE, Character.MIN_VALUE)));
      assertEquals(0, signum(Character.compare(Character.MAX_VALUE, Character.MAX_VALUE)));
    }
  }
}
