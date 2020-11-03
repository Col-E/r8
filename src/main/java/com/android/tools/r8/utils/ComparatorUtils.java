// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;
import java.util.Comparator;
import java.util.List;

public class ComparatorUtils {

  public static <T extends Comparable<T>> Comparator<List<T>> listComparator() {
    return listComparator(T::compareTo);
  }

  public static <T> Comparator<List<T>> listComparator(Comparator<T> comparator) {
    return (List<T> xs, List<T> ys) -> {
      int diff = Integer.compare(xs.size(), ys.size());
      for (int i = 0; i < xs.size() && diff == 0; i++) {
        diff = comparator.compare(xs.get(i), ys.get(i));
      }
      return diff;
    };
  }

  // Compare pair-wise integers in sequenced order, i.e., (A1, A2), (B1, B2), (C1, C2), ...
  public static int compareInts(int... ints) {
    assert ints.length % 2 == 0;
    int diff = 0;
    for (int i = 0; i < ints.length && diff == 0; ) {
      diff = Integer.compare(ints[i++], ints[i++]);
    }
    return diff;
  }

  public static int compareIntArray(int[] xs, int[] ys) {
    int diff = Integer.compare(xs.length, ys.length);
    for (int i = 0; i < xs.length && diff == 0; i++) {
      diff = Integer.compare(xs[i], ys[i]);
    }
    return diff;
  }

  public static int compareShortArray(short[] xs, short[] ys) {
    int diff = Integer.compare(xs.length, ys.length);
    for (int i = 0; i < xs.length && diff == 0; i++) {
      diff = Short.compare(xs[i], ys[i]);
    }
    return diff;
  }

  public static <T extends Comparable<T>> Comparator<T[]> arrayComparator() {
    return arrayComparator(T::compareTo);
  }

  public static <T> Comparator<T[]> arrayComparator(Comparator<T> comparator) {
    return (T[] xs, T[] ys) -> {
      int diff = Integer.compare(xs.length, ys.length);
      for (int i = 0; i < xs.length && diff == 0; i++) {
        diff = comparator.compare(xs[i], ys[i]);
      }
      return diff;
    };
  }

  public static <T> Comparator<T> unreachableComparator() {
    return (t1, t2) -> {
      throw new Unreachable();
    };
  }
}
