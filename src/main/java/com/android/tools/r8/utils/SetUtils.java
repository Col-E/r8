// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class SetUtils {

  public static <T> boolean containsAnyOf(Set<T> set, Iterable<T> elements) {
    if (set.isEmpty()) {
      return false;
    }
    for (T element : elements) {
      if (set.contains(element)) {
        return true;
      }
    }
    return false;
  }

  public static <T> Set<T> newConcurrentHashSet() {
    return ConcurrentHashMap.newKeySet();
  }

  public static <T> Set<T> newConcurrentHashSet(int capacity) {
    return ConcurrentHashMap.newKeySet(capacity);
  }

  public static <T> HashSet<T> newHashSet(T element) {
    HashSet<T> result = new HashSet<>(1);
    result.add(element);
    return result;
  }

  @SafeVarargs
  public static <T> HashSet<T> newHashSet(T... elements) {
    HashSet<T> result = new HashSet<>(elements.length);
    Collections.addAll(result, elements);
    return result;
  }

  public static <T> Set<T> newIdentityHashSet(T element) {
    Set<T> result = Sets.newIdentityHashSet();
    result.add(element);
    return result;
  }

  @SafeVarargs
  public static <T> Set<T> newIdentityHashSet(T... elements) {
    Set<T> result = newIdentityHashSet(elements.length);
    Collections.addAll(result, elements);
    return result;
  }

  public static <T> Set<T> newIdentityHashSet(ForEachable<T> forEachable) {
    Set<T> result = Sets.newIdentityHashSet();
    forEachable.forEach(result::add);
    return result;
  }

  public static <T> Set<T> newIdentityHashSet(Iterable<? extends T> c) {
    Set<T> result = Sets.newIdentityHashSet();
    c.forEach(result::add);
    return result;
  }

  public static <T> Set<T> newIdentityHashSet(Iterable<T> c1, Iterable<T> c2) {
    Set<T> result = Sets.newIdentityHashSet();
    c1.forEach(result::add);
    c2.forEach(result::add);
    return result;
  }

  public static <T> Set<T> newIdentityHashSet(Iterable<T> c1, Iterable<T> c2, Iterable<T> c3) {
    Set<T> result = Sets.newIdentityHashSet();
    c1.forEach(result::add);
    c2.forEach(result::add);
    c3.forEach(result::add);
    return result;
  }

  public static <T> Set<T> newIdentityHashSet(int capacity) {
    return Collections.newSetFromMap(new IdentityHashMap<>(capacity));
  }

  public static <T> Set<T> newIdentityHashSet(T element, int capacity) {
    Set<T> result = newIdentityHashSet(capacity);
    result.add(element);
    return result;
  }

  public static <T> ImmutableSet<T> newImmutableSet(ForEachable<T> forEachable) {
    ImmutableSet.Builder<T> builder = ImmutableSet.builder();
    forEachable.forEach(builder::add);
    return builder.build();
  }

  @SafeVarargs
  public static <T> ImmutableSet<T> newImmutableSetExcludingNullItems(T... items) {
    ImmutableSet.Builder<T> builder = ImmutableSet.builder();
    for (T item : items) {
      if (item != null) {
        builder.add(item);
      }
    }
    return builder.build();
  }

  public static <T, S> Set<T> mapIdentityHashSet(Set<S> set, Function<S, T> fn) {
    Set<T> out = newIdentityHashSet(set.size());
    for (S element : set) {
      out.add(fn.apply(element));
    }
    return out;
  }

  public static <T> T removeFirst(Set<T> set) {
    T element = set.iterator().next();
    set.remove(element);
    return element;
  }

  public static <T> Set<T> trimCapacityOfIdentityHashSetIfSizeLessThan(
      Set<T> set, int expectedSize) {
    if (set.size() < expectedSize) {
      Set<T> newSet = SetUtils.newIdentityHashSet(set.size());
      newSet.addAll(set);
      return newSet;
    }
    return set;
  }

  public static <T> Set<T> unionIdentityHashSet(Set<T> one, Set<T> other) {
    Set<T> union = Sets.newIdentityHashSet();
    union.addAll(one);
    union.addAll(other);
    return union;
  }

  public static <K> Set<K> unmodifiableForTesting(Set<K> map) {
    return InternalOptions.assertionsEnabled() ? Collections.unmodifiableSet(map) : map;
  }
}
