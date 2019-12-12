// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CollectionsMethods {

  public static <T> Enumeration<T> emptyEnumeration() {
    return Collections.enumeration(Collections.emptyList());
  }

  public static <T> Iterator<T> emptyIterator() {
    return Collections.<T>emptyList().iterator();
  }

  public static <T> ListIterator<T> emptyListIterator() {
    return Collections.<T>emptyList().listIterator();
  }

  public static <T> List<T> copyOfList(Collection<? extends T> other) {
    ArrayList<T> list = new ArrayList<>(other.size());
    for (T item : other) {
      list.add(Objects.requireNonNull(item));
    }
    return Collections.unmodifiableList(list);
  }

  public static <T> Set<T> copyOfSet(Collection<? extends T> other) {
    HashSet<T> set = new HashSet<>(other.size());
    for (T item : other) {
      set.add(Objects.requireNonNull(item));
    }
    return Collections.unmodifiableSet(set);
  }

  public static <K, V> Map<K, V> copyOfMap(Map<? extends K, ? extends V> other) {
    HashMap<K, V> map = new HashMap<>(other.size());
    for (Map.Entry<? extends K, ? extends V> entry : other.entrySet()) {
      map.put(
          Objects.requireNonNull(entry.getKey()),
          Objects.requireNonNull(entry.getValue()));
    }
    return Collections.unmodifiableMap(map);
  }
}
