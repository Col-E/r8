// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import java.util.Comparator;
import java.util.function.BiFunction;

public abstract class AmbiguousComparator<T> implements Comparator<T> {

  public enum SortKeys {
    CLASS,
    METHOD,
    SOURCE,
    LINE
  }

  private final BiFunction<T, SortKeys, String> getter;

  public AmbiguousComparator(BiFunction<T, SortKeys, String> getter) {
    this.getter = getter;
  }

  @Override
  public int compare(T o1, T o2) {
    int compare = getter.apply(o1, SortKeys.CLASS).compareTo(getter.apply(o2, SortKeys.CLASS));
    if (compare != 0) {
      return compare;
    }
    compare = getter.apply(o1, SortKeys.METHOD).compareTo(getter.apply(o2, SortKeys.METHOD));
    if (compare != 0) {
      return compare;
    }
    compare = getter.apply(o1, SortKeys.SOURCE).compareTo(getter.apply(o2, SortKeys.SOURCE));
    if (compare != 0) {
      return compare;
    }
    try {
      return Integer.compare(
          Integer.parseInt(getter.apply(o1, SortKeys.LINE)),
          Integer.parseInt(getter.apply(o2, SortKeys.SOURCE)));
    } catch (NumberFormatException ignore) {
      return 0;
    }
  }
}
