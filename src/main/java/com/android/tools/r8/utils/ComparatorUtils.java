// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ComparatorUtils {

  public static <T extends Comparable<T>> Comparator<List<T>> listComparator() {
    return listComparator(T::compareTo);
  }

  public static <T> Comparator<List<T>> listComparator(Comparator<T> comparator) {
    return (List<T> xs, List<T> ys) -> compareLists(xs, ys, comparator);
  }

  public static <T extends Comparable<T>> int compareLists(List<T> xs, List<T> ys) {
    return compareLists(xs, ys, T::compareTo);
  }

  public static <T> int compareLists(List<T> xs, List<T> ys, Comparator<T> comparator) {
    int diff = Integer.compare(xs.size(), ys.size());
    for (int i = 0; i < xs.size() && diff == 0; i++) {
      diff = comparator.compare(xs.get(i), ys.get(i));
    }
    return diff;
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

  public static <S> Comparator<Int2ReferenceMap<S>> int2ReferenceMapComparator(
      Comparator<S> comparator) {
    return (map1, map2) -> compareInt2ReferenceMap(map1, map2, comparator);
  }

  public static <S> int compareInt2ReferenceMap(
      Int2ReferenceMap<S> map1, Int2ReferenceMap<S> map2, Comparator<S> comparator) {
    int sizeDiff = Integer.compare(map1.size(), map2.size());
    if (sizeDiff != 0) {
      return sizeDiff;
    }
    if (map1.isEmpty()) {
      assert map2.isEmpty();
      return 0;
    }
    Integer minMissing1 = findSmallestMissingKey(map1, map2);
    Integer minMissing2 = findSmallestMissingKey(map2, map1);
    if (minMissing1 != null) {
      assert minMissing2 != null;
      return minMissing1.compareTo(minMissing2);
    }
    // Keys are equal so compare the values point-wise sorted by the keys.
    ArrayList<Integer> keys = new ArrayList<>(map1.keySet());
    keys.sort(Integer::compareTo);
    for (int key : keys) {
      S item1 = map1.get(key);
      S item2 = map2.get(key);
      int diff = comparator.compare(item1, item2);
      if (diff != 0) {
        return diff;
      }
    }
    return 0;
  }

  private static <S> Integer findSmallestMissingKey(
      Int2ReferenceMap<S> map1, Int2ReferenceMap<S> map2) {
    boolean hasMissing = false;
    int missing = Integer.MAX_VALUE;
    for (int key : map1.keySet()) {
      if (!map2.containsKey(key)) {
        missing = hasMissing ? Math.min(missing, key) : key;
        hasMissing = true;
      }
    }
    return hasMissing ? missing : null;
  }
}
