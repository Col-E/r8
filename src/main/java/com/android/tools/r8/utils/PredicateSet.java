// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class PredicateSet<T> {

  private final Set<T> elements = Sets.newIdentityHashSet();
  private final List<Predicate<T>> predicates = new ArrayList<>();

  public boolean addElement(T element) {
    return elements.add(element);
  }

  public void addPredicate(Predicate<T> predicate) {
    predicates.add(predicate);
  }

  public PredicateSet<T> rewriteItems(Function<T, T> mapping) {
    PredicateSet<T> set = new PredicateSet<>();
    for (T item : elements) {
      set.elements.add(mapping.apply(item));
    }
    // It is assumed that the predicates do not need rewriting. Otherwise, this method must be
    // overwritten.
    set.predicates.addAll(predicates);
    return set;
  }

  public boolean contains(T element) {
    if (elements.contains(element)) {
      return true;
    }
    for (Predicate<T> predicate : predicates) {
      if (predicate.test(element)) {
        return true;
      }
    }
    return false;
  }
}
