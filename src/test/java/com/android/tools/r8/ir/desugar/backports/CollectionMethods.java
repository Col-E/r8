// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
}
