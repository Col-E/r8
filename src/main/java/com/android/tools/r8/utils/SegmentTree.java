// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Implementation of a discrete segment tree where intervals are specified by their start and end
 * point. Both points are considered part of the interval.
 */
public class SegmentTree<V> {

  private final TreeMap<Integer, V> internalTree = new TreeMap<>();
  private final boolean allowIntervalOverwrites;

  private int size = 0;

  public SegmentTree(boolean allowIntervalOverwrites) {
    this.allowIntervalOverwrites = allowIntervalOverwrites;
  }

  public V find(int point) {
    Map.Entry<Integer, V> entry = findEntry(point);
    return entry != null ? entry.getValue() : null;
  }

  public Map.Entry<Integer, V> findEntry(Integer point) {
    Map.Entry<Integer, V> kvEntry = internalTree.floorEntry(point);
    return (kvEntry == null || kvEntry.getValue() == null) ? null : kvEntry;
  }

  public SegmentTree<V> add(int start, int end, V value) {
    Map.Entry<Integer, V> existingEndRange = findEntry(end);
    Box<Integer> removedIntervals = new Box<>(0);
    boolean removedKeys =
        internalTree
            .navigableKeySet()
            .removeIf(
                key -> {
                  if (start < key && key <= end) {
                    assert allowIntervalOverwrites;
                    if (internalTree.get(key) != null) {
                      removedIntervals.set(removedIntervals.get() + 1);
                    }
                    return true;
                  }
                  return false;
                });
    if (existingEndRange != null) {
      assert allowIntervalOverwrites;
      if (removedKeys) {
        // We have counted a removed interval where it was actually just shortened:
        // I1      |----------------------------------|
        // I2   |--------|
        // R    |--------||---------------------------|
        // I1.start has been removed and counted, but it is actually just moved, so we decrease the
        // removed counter by 1.
        removedIntervals.set(removedIntervals.get() - 1);
      }
    }
    internalTree.put(start, value);
    if (!internalTree.containsKey(end + 1)) {
      internalTree.put(end + 1, existingEndRange == null ? null : existingEndRange.getValue());
    }
    // We only count unique intervals, thus the following is two and not three intervals when
    // adding I2:
    // I1      |-------------------------------|
    // I2              |------------|
    // R       |------||------------||---------|
    // However, if the order was reversed, we should remove one from the size count since I1
    // completely shadows I2.
    size = (size - removedIntervals.get()) + 1;
    return this;
  }

  public int size() {
    return size;
  }

  public void removeSegment(int start) {
    if (internalTree.remove(start) != null) {
      size = size - 1;
    }
    if (size == 0) {
      internalTree.clear();
    }
  }

  public void visitSegments(Consumer<V> consumer) {
    internalTree
        .values()
        .forEach(
            segment -> {
              if (segment != null) {
                consumer.accept(segment);
              }
            });
  }
}
