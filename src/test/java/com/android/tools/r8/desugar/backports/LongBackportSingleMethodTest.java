// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// This test uses only parseUnsignedLongWithRadix and toUnsignedStringWithRadix, which call
// divideUnsigned, but NOT divideUnsigned. D8 has to create a utility class for divideUnsigned, but
// D8 can do that only once the previously created utility classes methods have been optimized.
// This used to fail with the message "Missing utility class in the output"
// (Missing utility class for divideUnsigned).
@RunWith(Parameterized.class)
public final class LongBackportSingleMethodTest extends AbstractBackportTest {

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public LongBackportSingleMethodTest(TestParameters parameters) {
    super(parameters, Long.class, Main.class);
    registerTarget(AndroidApiLevel.O, 2);
  }

  static final class Main extends MiniAssert {

    public static void main(String[] args) {
      assertTrue(Long.parseUnsignedLong("1234", 8) == 668);
      assertTrue(Long.toUnsignedString(1234L, 8).equals("2322"));
    }
  }
}
