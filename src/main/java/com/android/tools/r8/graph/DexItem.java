// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.MixedSectionCollection;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.stream.Stream;

public abstract class DexItem {

  public static <T extends DexItem> void collectAll(MixedSectionCollection mixedItems, T[] items) {
    consumeArray(items, item -> item.collectMixedSectionItems(mixedItems));
  }

  public static <T extends DexItem> void collectAll(MixedSectionCollection mixedItems,
      Collection<T> items) {
    items.forEach(item -> item.collectMixedSectionItems(mixedItems));
  }

  /**
   * Helper method to iterate over elements in an array.
   * Handles the case where the array is null.
   */
  private static <T extends DexItem> void consumeArray(T[] items, Consumer<T> consumer) {
    if (items == null) {
      return;
    }
    for (T item : items) {
      if (item != null) {
        consumer.accept(item);
      }
    }
  }

  protected abstract void collectMixedSectionItems(MixedSectionCollection collection);

  protected void flushCachedValues() {
    // Overwritten in subclasses.
  }

  public String toSmaliString() {
    return toString();
  }

  public String toSourceString() {
    return toString();
  }

  static <T extends DexItem> Stream<T> filter(Stream<DexItem> stream, Class<T> clazz) {
    return stream.filter(clazz::isInstance).map(clazz::cast);
  }

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();
}
