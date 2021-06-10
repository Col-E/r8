// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

public class SetUtils {

  public static <T> Set<T> newIdentityHashSet(T element) {
    Set<T> result = Sets.newIdentityHashSet();
    result.add(element);
    return result;
  }

  public static <T> Set<T> newIdentityHashSet(T[] elements) {
    Set<T> result = Sets.newIdentityHashSet();
    Collections.addAll(result, elements);
    return result;
  }

  public static <T> Set<T> newIdentityHashSet(ForEachable<T> forEachable) {
    Set<T> result = Sets.newIdentityHashSet();
    forEachable.forEach(result::add);
    return result;
  }

  public static <T> Set<T> newIdentityHashSet(Iterable<T> c) {
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

  public static <T, S> Set<T> mapIdentityHashSet(Set<S> set, Function<S, T> fn) {
    Set<T> out = newIdentityHashSet(set.size());
    for (S element : set) {
      out.add(fn.apply(element));
    }
    return out;
  }

  public static <T> Set<T> unionIdentityHashSet(Set<T> one, Set<T> other) {
    Set<T> union = Sets.newIdentityHashSet();
    union.addAll(one);
    union.addAll(other);
    return union;
  }
}
