// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.Collection;
import java.util.function.Consumer;

public class OneShotCollectionConsumer<T> {

  private final Collection<T> collection;
  private boolean hasBeenConsumed = false;

  private OneShotCollectionConsumer(Collection<T> collection) {
    this.collection = collection;
  }

  public void add(T t) {
    assert !hasBeenConsumed;
    this.collection.add(t);
  }

  public void consume(Consumer<T> consumer) {
    hasBeenConsumed = true;
    collection.forEach(consumer);
    collection.clear();
  }

  public static <T> OneShotCollectionConsumer<T> wrap(Collection<T> collection) {
    return new OneShotCollectionConsumer<>(collection);
  }

  public boolean isEmpty() {
    return collection.isEmpty();
  }
}
