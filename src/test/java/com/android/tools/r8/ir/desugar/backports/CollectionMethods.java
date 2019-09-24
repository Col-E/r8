// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CollectionMethods {

  public static <E> List<E> listOfArray(E[] elements) {
    ArrayList<E> list = new ArrayList<>(elements.length);
    for (E element : elements) {
      list.add(Objects.requireNonNull(element));
    }
    return Collections.unmodifiableList(list);
  }

  public static <E> Set<E> setOfArray(E[] elements) {
    HashSet<E> set = new HashSet<>(elements.length);
    for (E element : elements) {
      if (!set.add(Objects.requireNonNull(element))) {
        throw new IllegalArgumentException("duplicate element: " + element);
      }
    }
    return Collections.unmodifiableSet(set);
  }

  public static <K, V> Map<K, V> mapOfEntries(Map.Entry<K, V>[] elements) {
    HashMap<K, V> map = new HashMap<>(elements.length);
    for (Map.Entry<K, V> element : elements) {
      K key = Objects.requireNonNull(element.getKey());
      V value = Objects.requireNonNull(element.getValue());
      if (map.put(key, value) != null) {
        throw new IllegalArgumentException("duplicate key: " + key);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  public static <K, V> Map.Entry<K, V> mapEntry(K key, V value) {
    return new AbstractMap.SimpleImmutableEntry<>(
        Objects.requireNonNull(key),
        Objects.requireNonNull(value));
  }
}
