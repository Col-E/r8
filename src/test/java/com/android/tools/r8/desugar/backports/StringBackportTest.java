// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

@RunWith(Parameterized.class)
public final class StringBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withAllRuntimesAndApiLevels()
        .build();
  }

  public StringBackportTest(TestParameters parameters) {
    super(parameters, String.class, Main.class);
    registerTarget(AndroidApiLevel.O, 12);
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testJoinArray();
      testJoinIterable();
    }

    private static void testJoinArray() {
      assertEquals("", String.join(", "));
      assertEquals("one", String.join(", ", "one"));
      assertEquals("one, two, three", String.join(", ", "one", "two", "three"));
      assertEquals("onetwothree", String.join("", "one", "two", "three"));

      try {
        throw new AssertionError(String.join(null, "one", "two", "three"));
      } catch (NullPointerException expected) {
      }
      try {
        throw new AssertionError(String.join(", ", (CharSequence[]) null));
      } catch (NullPointerException expected) {
      }
    }

    private static void testJoinIterable() {
      assertEquals("", String.join(", ", emptyList()));
      assertEquals("one", String.join(", ", asList("one")));
      assertEquals("one, two, three", String.join(", ", asList("one", "two", "three")));
      assertEquals("onetwothree", String.join("", asList("one", "two", "three")));

      try {
        throw new AssertionError(String.join(null, asList("one", "two", "three")));
      } catch (NullPointerException expected) {
      }
      try {
        throw new AssertionError(String.join(", ", (Iterable<CharSequence>) null));
      } catch (NullPointerException expected) {
      }
    }
  }
}
