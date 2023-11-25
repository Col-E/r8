// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

public abstract class PositionRangeAllocator {

  private static final int MAX_POSITION = 256;
  private static final int MAX_DELTA = 1;

  final Range[] cache = new Range[MAX_POSITION];

  public Range get(int index) {
    return (index >= 0 && index < MAX_POSITION) ? cache[index] : new Range(index);
  }

  public static CardinalPositionRangeAllocator createCardinalPositionRangeAllocator() {
    return new CardinalPositionRangeAllocator();
  }

  public static NonCardinalPositionRangeAllocator createNonCardinalPositionRangeAllocator() {
    return new NonCardinalPositionRangeAllocator();
  }

  public static class CardinalPositionRangeAllocator extends PositionRangeAllocator {

    private CardinalPositionRangeAllocator() {
      super();
      for (int i = 0; i < MAX_POSITION; i++) {
        cache[i] = new Range(i);
      }
    }
  }

  public static class NonCardinalPositionRangeFixedDeltaCache extends PositionRangeAllocator {

    public NonCardinalPositionRangeFixedDeltaCache(int delta) {
      super();
      for (int i = 0; i < MAX_POSITION; i++) {
        cache[i] = new Range(i, i + delta);
      }
    }
  }

  public static class NonCardinalPositionRangeAllocator extends PositionRangeAllocator {

    private final NonCardinalPositionRangeFixedDeltaCache[]
        nonCardinalPositionRangeFixedDeltaCache =
            new NonCardinalPositionRangeFixedDeltaCache[MAX_DELTA + 1];

    private NonCardinalPositionRangeAllocator() {
      for (int i = 0; i <= MAX_DELTA; i++) {
        nonCardinalPositionRangeFixedDeltaCache[i] = new NonCardinalPositionRangeFixedDeltaCache(i);
      }
    }

    public Range get(int from, int to) {
      if (from >= MAX_POSITION) {
        return new Range(from, to);
      }
      int thisDelta = to - from;
      if (thisDelta < 0) {
        return new Range(from, to);
      }
      if (thisDelta > MAX_DELTA) {
        return new Range(from, to);
      }
      return nonCardinalPositionRangeFixedDeltaCache[thisDelta].get(from);
    }
  }
}
