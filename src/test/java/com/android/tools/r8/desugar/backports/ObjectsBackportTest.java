// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class ObjectsBackportTest extends AbstractBackportTest {
  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withAllRuntimesAndApiLevels()
        .build();
  }

  public ObjectsBackportTest(TestParameters parameters) {
    super(parameters, Objects.class, Main.class);
    registerTarget(AndroidApiLevel.N, 59);
    registerTarget(AndroidApiLevel.K, 55);
  }

  static final class Main extends MiniAssert {
    public static void main(String[] args) {
      testCompare();
      testDeepEquals();
      testEquals();
      testHash();
      testHashCode();
      testIsNull();
      testNonNull();
      testToString();
      testToStringOrDefault();
      testRequireNonNull();
      testRequireNonNullOrMessage();
    }

    private static void testCompare() {
      Comparator<String> stringsNullLast = (o1, o2) -> {
        if (o1 == null) {
          return o2 == null ? 0 : 1;
        }
        return o2 == null ? -1 : o1.compareTo(o2);
      };

      assertEquals(-1, Objects.compare("a", "b", stringsNullLast));
      assertEquals(0, Objects.compare("a", "a", stringsNullLast));
      assertEquals(1, Objects.compare("b", "a", stringsNullLast));

      assertEquals(-1, Objects.compare("a", null, stringsNullLast));
      assertEquals(0, Objects.compare(null, null, stringsNullLast));
      assertEquals(1, Objects.compare(null, "a", stringsNullLast));
    }

    private static void testDeepEquals() {
      assertTrue(Objects.deepEquals(null, null));

      assertTrue(Objects.deepEquals(true, true));
      assertTrue(Objects.deepEquals((byte) 1, (byte) 1));
      assertTrue(Objects.deepEquals('a', 'a'));
      assertTrue(Objects.deepEquals((double) 1, (double) 1));
      assertTrue(Objects.deepEquals(1f, 1f));
      assertTrue(Objects.deepEquals(1, 1));
      assertTrue(Objects.deepEquals(1L, 1L));
      assertTrue(Objects.deepEquals((short) 1, (short) 1));
      assertTrue(Objects.deepEquals("abc", "abc"));

      // Test primitive arrays against another instance with the same contents.
      assertTrue(Objects.deepEquals(new boolean[] { true, false, true },
          new boolean[] { true, false, true }));
      assertTrue(Objects.deepEquals(new byte[] { 4, 8, 15, 16, 23, 42 },
          new byte[] { 4, 8, 15, 16, 23, 42 }));
      assertTrue(Objects.deepEquals(new char[] { 4, 8, 15, 16, 23, 42 },
          new char[] { 4, 8, 15, 16, 23, 42 }));
      assertTrue(Objects.deepEquals(new double[] { 4, 8, 15, 16, 23, 42 },
          new double[] { 4, 8, 15, 16, 23, 42 }));
      assertTrue(Objects.deepEquals(new float[] { 4, 8, 15, 16, 23, 42 },
          new float[] { 4, 8, 15, 16, 23, 42 }));
      assertTrue(Objects.deepEquals(new int[] { 4, 8, 15, 16, 23, 42 },
          new int[] { 4, 8, 15, 16, 23, 42 }));
      assertTrue(Objects.deepEquals(new long[] { 4, 8, 15, 16, 23, 42 },
          new long[] { 4, 8, 15, 16, 23, 42 }));
      assertTrue(Objects.deepEquals(new short[] { 4, 8, 15, 16, 23, 42 },
          new short[] { 4, 8, 15, 16, 23, 42 }));

      // Test Object arrays against other arrays of the same contents, including nested arrays.
      assertTrue(Objects.deepEquals(new Object[] { (byte) 4, (char) 8, 15d, 16f, 23, 42L },
          new Object[] { (byte) 4, (char) 8, 15d, 16f, 23, 42L }));
      assertTrue(Objects.deepEquals(new Object[] {
          new boolean[] { true, false },
          "hey",
          new int[] { 4, 8, 15, 16, 23, 42 },
          new Object[] {
              new long[] { 1, 2, 4, 8, 16, 32 },
              "hello",
          },
      }, new Object[] {
          new boolean[] { true, false },
          "hey",
          new int[] { 4, 8, 15, 16, 23, 42 },
          new Object[] {
              new long[] { 1, 2, 4, 8, 16, 32 },
              "hello",
          },
      }));

      // Test primitive arrays against incompatible types or the same type with different contents.
      Object[] testArrays = {
          new boolean[] { false, true, false },
          new byte[] { 1, 2, 4, 8, 16, 32 },
          new char[] { 1, 2, 4, 8, 16, 32 },
          new double[] { 1, 2, 4, 8, 16, 32 },
          new float[] { 1, 2, 4, 8, 16, 32 },
          new int[] { 1, 2, 4, 8, 16, 32 },
          new long[] { 1, 2, 4, 8, 16, 32 },
          new short[] { 1, 2, 4, 8, 16, 32 },
      };
      for (Object testArray : testArrays) {
        assertFalse(Objects.deepEquals(testArray, new boolean[] { true, false, true }));
        assertFalse(Objects.deepEquals(testArray, new byte[] { 4, 8, 15, 16, 23, 42 }));
        assertFalse(Objects.deepEquals(testArray, new char[] { 4, 8, 15, 16, 23, 42 }));
        assertFalse(Objects.deepEquals(testArray, new double[] { 4, 8, 15, 16, 23, 42 }));
        assertFalse(Objects.deepEquals(testArray, new float[] { 4, 8, 15, 16, 23, 42 }));
        assertFalse(Objects.deepEquals(testArray, new int[] { 4, 8, 15, 16, 23, 42 }));
        assertFalse(Objects.deepEquals(testArray, new long[] { 4, 8, 15, 16, 23, 42 }));
        assertFalse(Objects.deepEquals(testArray, new short[] { 4, 8, 15, 16, 23, 42 }));
        assertFalse(Objects.deepEquals(testArray,
            new Object[] { false, (byte) 4, (char) 8, 15d, 16f, 23, 42L, (short) 108 }));
      }
    }

    private static void testHash() {
      assertEquals(0, Objects.hash((Object[]) null));
      assertEquals(1, Objects.hash());

      assertEquals(Arrays.hashCode(new Object[] { "a" }), Objects.hash("a"));
      assertEquals(Arrays.hashCode(new Object[] { 1, "2", 3d, 4f, 5L }),
          Objects.hash(1, "2", 3d, 4f, 5L));
    }

    private static void testHashCode() {
      assertEquals(0, Objects.hashCode(null));

      // Ensure we invoke hashCode() and propagate its result.
      assertEquals(5, Objects.hashCode(new Object() {
        @Override public int hashCode() {
          return 5;
        }
      }));
    }

    private static void testEquals() {
      assertTrue(Objects.equals(null, null));

      assertFalse(Objects.equals(null, "non-null"));
      assertFalse(Objects.equals("non-null", null));

      // Ensure we invoke equals() and propagate its result.
      Object second = new Object();
      assertTrue(Objects.equals(new Object() {
        @Override public boolean equals(Object obj) {
          assertSame(obj, second);
          return true;
        }
      }, second));
      assertFalse(Objects.equals(new Object() {
        @Override public boolean equals(Object obj) {
          assertSame(obj, second);
          return false;
        }
      }, second));
    }

    private static void testIsNull() {
      assertTrue(Objects.isNull(null));
      assertFalse(Objects.isNull("non-null"));
    }

    private static void testNonNull() {
      assertTrue(Objects.nonNull("non-null"));
      assertFalse(Objects.nonNull(null));
    }

    private static void testToString() {
      assertEquals("null", Objects.toString(null));

      // Ensure we invoke toString() and propagate its result.
      assertEquals("non-null", Objects.toString(new Object() {
        @Override public String toString() {
          return "non-null";
        }
      }));
    }

    private static void testToStringOrDefault() {
      assertEquals(null, Objects.toString(null, null));
      assertEquals("null default", Objects.toString(null, "null default"));

      // Ensure we invoke toString() and propagate its result.
      assertEquals("non-null", Objects.toString(new Object() {
        @Override public String toString() {
          return "non-null";
        }
      }, "null default"));
    }

    private static void testRequireNonNull() {
      Object o = new Object();
      assertSame(o, Objects.requireNonNull(o));

      try {
        throw new AssertionError(Objects.requireNonNull(null));
      } catch (NullPointerException expected) {
      }
    }

    private static void testRequireNonNullOrMessage() {
      Object o = new Object();
      assertSame(o, Objects.requireNonNull(o, "unexpected"));

      try {
        throw new AssertionError(Objects.requireNonNull(null, "expected"));
      } catch (NullPointerException e) {
        assertTrue(e.getMessage().contains("expected"));
      }
    }
  }
}
