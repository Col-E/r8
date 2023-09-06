// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Comparator;

public enum CompareResult {
  LESS_THAN(-1),
  EQUAL(0),
  GREATER_THAN(1);

  private final int comparisonResult;

  CompareResult(int comparisonResult) {
    this.comparisonResult = comparisonResult;
  }

  public int getComparisonResult() {
    return comparisonResult;
  }

  public boolean isEqual() {
    return this == EQUAL;
  }

  public static <T extends Comparable<T>> CompareResult compare(T element, T other) {
    return fromComparisonResult(element.compareTo(other));
  }

  public static <T> CompareResult compare(T element, T other, Comparator<T> comparator) {
    return fromComparisonResult(comparator.compare(element, other));
  }

  public static CompareResult fromComparisonResult(int comparisonResult) {
    if (comparisonResult < 0) {
      return CompareResult.LESS_THAN;
    }
    return comparisonResult == 0 ? CompareResult.EQUAL : CompareResult.GREATER_THAN;
  }
}
