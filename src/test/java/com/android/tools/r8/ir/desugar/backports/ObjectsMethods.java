// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Supplier;

public final class ObjectsMethods {

  public static <T> int compare(T a, T b, Comparator<? super T> c) {
    return a == b ? 0 : c.compare(a, b);
  }

  public static boolean deepEquals(Object a, Object b) {
    if (a == b) return true;
    if (a == null) return false;
    if (a instanceof boolean[]) {
      return b instanceof boolean[] && Arrays.equals((boolean[]) a, (boolean[]) b);
    }
    if (a instanceof byte[]) {
      return b instanceof byte[] && Arrays.equals((byte[]) a, (byte[]) b);
    }
    if (a instanceof char[]) {
      return b instanceof char[] && Arrays.equals((char[]) a, (char[]) b);
    }
    if (a instanceof double[]) {
      return b instanceof double[] && Arrays.equals((double[]) a, (double[]) b);
    }
    if (a instanceof float[]) {
      return b instanceof float[] && Arrays.equals((float[]) a, (float[]) b);
    }
    if (a instanceof int[]) {
      return b instanceof int[] && Arrays.equals((int[]) a, (int[]) b);
    }
    if (a instanceof long[]) {
      return b instanceof long[] && Arrays.equals((long[]) a, (long[]) b);
    }
    if (a instanceof short[]) {
      return b instanceof short[] && Arrays.equals((short[]) a, (short[]) b);
    }
    if (a instanceof Object[]) {
      return b instanceof Object[] && Arrays.deepEquals((Object[]) a, (Object[]) b);
    }
    return a.equals(b);
  }

  public static boolean equals(Object a, Object b) {
    return a == b || (a != null && a.equals(b));
  }

  public static int hashCode(Object o) {
    return o == null ? 0 : o.hashCode();
  }

  public static boolean isNull(Object o) {
    return o == null;
  }

  public static boolean nonNull(Object o) {
    return o != null;
  }

  public static <T> T requireNonNullMessage(T obj, String message) {
    if (obj == null) {
      throw new NullPointerException(message);
    }
    return obj;
  }

  public static <T> T requireNonNullSupplier(T obj, Supplier<String> messageSupplier) {
    if (obj == null) {
      // While calling `messageSupplier.get()` unconditionally would produce the correct behavior,
      // some ART versions add an exception message to seemingly-unintended null dereferences along
      // the lines of "Attempted to invoke interface method Supplier.get() on a null reference"
      // which we don't want to expose as the reference implementation has a null message.
      String message = messageSupplier != null ? messageSupplier.get() : null;
      throw new NullPointerException(message);
    }
    return obj;
  }

  public static <T> T requireNonNullElse(T obj, T defaultObj) {
    if (obj != null) return obj;
    return Objects.requireNonNull(defaultObj, "defaultObj");
  }

  public static <T> T requireNonNullElseGet(T obj, Supplier<? extends T> supplier) {
    if (obj != null) return obj;
    T defaultObj = Objects.requireNonNull(supplier, "supplier").get();
    return Objects.requireNonNull(defaultObj, "supplier.get()");
  }

  public static String toString(Object o) {
    return Objects.toString(o, "null");
  }

  public static String toStringDefault(Object o, String nullDefault) {
    return o == null ? nullDefault : o.toString();
  }

  public static int checkIndex(int index, int length) {
    if (index < 0 || index >= length) {
      throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
    }
    return index;
  }

  public static int checkFromToIndex(int fromIndex, int toIndex, int length) {
    if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) {
      throw new IndexOutOfBoundsException(
          "Range [" + fromIndex + ", " + toIndex + ") out of bounds for length " + length);
    }
    return fromIndex;
  }

  public static int checkFromIndexSize(int fromIndex, int size, int length) {
    if (fromIndex < 0 || size < 0 || length < 0 || fromIndex > length - size) {
      throw new IndexOutOfBoundsException(
          "Range ["
              + fromIndex
              + ", "
              + fromIndex
              + " + "
              + size
              + ") out of bounds for length "
              + length);
    }
    return fromIndex;
  }

  public static long checkIndexLong(long index, long length) {
    if (index < 0 || index >= length) {
      throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
    }
    return index;
  }

  public static long checkFromToIndexLong(long fromIndex, long toIndex, long length) {
    if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) {
      throw new IndexOutOfBoundsException(
          "Range [" + fromIndex + ", " + toIndex + ") out of bounds for length " + length);
    }
    return fromIndex;
  }

  public static long checkFromIndexSizeLong(long fromIndex, long size, long length) {
    if (fromIndex < 0 || size < 0 || length < 0 || fromIndex > length - size) {
      throw new IndexOutOfBoundsException(
          "Range ["
              + fromIndex
              + ", "
              + fromIndex
              + " + "
              + size
              + ") out of bounds for length "
              + length);
    }
    return fromIndex;
  }
}
