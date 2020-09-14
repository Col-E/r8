// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import java.util.Comparator;
import java.util.List;

public class ComparatorUtils {

  public static <T extends Comparable<T>> Comparator<List<T>> listComparator() {
    return listComparator(T::compareTo);
  }

  public static <T> Comparator<List<T>> listComparator(Comparator<T> comparator) {
    return (List<T> xs, List<T> ys) -> {
      int sizeDiff = Integer.compare(xs.size(), ys.size());
      if (sizeDiff != 0) {
        return sizeDiff;
      }
      for (int i = 0; i < xs.size(); i++) {
        int elementDiff = comparator.compare(xs.get(i), ys.get(i));
        if (elementDiff != 0) {
          return elementDiff;
        }
      }
      return 0;
    };
  }

  public static int compareIntArray(int[] xs, int[] ys) {
    int sizeDiff = Integer.compare(xs.length, ys.length);
    if (sizeDiff != 0) {
      return sizeDiff;
    }
    for (int i = 0; i < xs.length; i++) {
      int elementDiff = Integer.compare(xs[i], ys[i]);
      if (elementDiff != 0) {
        return elementDiff;
      }
    }
    return 0;
  }

  public static <T extends Comparable<T>> Comparator<T[]> arrayComparator() {
    return arrayComparator(T::compareTo);
  }

  public static <T> Comparator<T[]> arrayComparator(Comparator<T> comparator) {
    return (T[] xs, T[] ys) -> {
      int sizeDiff = Integer.compare(xs.length, ys.length);
      if (sizeDiff != 0) {
        return sizeDiff;
      }
      for (int i = 0; i < xs.length; i++) {
        int elementDiff = comparator.compare(xs[i], ys[i]);
        if (elementDiff != 0) {
          return elementDiff;
        }
      }
      return 0;
    };
  }
}
