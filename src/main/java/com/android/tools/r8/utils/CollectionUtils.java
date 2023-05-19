// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class CollectionUtils {

  public static <S, T extends Collection<S>> T addAll(T collection, Collection<S> elementsToAdd) {
    collection.addAll(elementsToAdd);
    return collection;
  }

  public static <T> T getFirst(Collection<T> collection) {
    return collection.iterator().next();
  }

  public static <T> T getFirstOrDefault(Collection<T> collection, T defaultValue) {
    return collection.isEmpty() ? defaultValue : getFirst(collection);
  }

  public static <T> Set<T> mergeSets(Collection<T> first, Collection<T> second) {
    ImmutableSet.Builder<T> builder = ImmutableSet.builder();
    builder.addAll(first);
    builder.addAll(second);
    return builder.build();
  }

  @SafeVarargs
  public static <T> void forEach(Consumer<T> consumer, Collection<T>... collections) {
    for (Collection<T> collection : collections) {
      collection.forEach(consumer);
    }
  }

  public static <T extends Comparable<T>> Collection<T> sort(Collection<T> items) {
    ArrayList<T> ts = new ArrayList<>(items);
    Collections.sort(ts);
    return ts;
  }

  public static <T> Collection<T> sort(Collection<T> items, Comparator<T> comparator) {
    ArrayList<T> ts = new ArrayList<>(items);
    ts.sort(comparator);
    return ts;
  }

  public static <T> String[] mapToStringArray(Collection<T> items, Function<T, String> mapper) {
    String[] returnArr = new String[items.size()];
    int index = 0;
    for (T item : items) {
      returnArr[index++] = mapper.apply(item);
    }
    return returnArr;
  }
}
