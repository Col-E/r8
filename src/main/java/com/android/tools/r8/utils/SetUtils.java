// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.google.common.collect.Sets;
import java.util.Set;

public class SetUtils {

  public static <T> Set<T> newIdentityHashSet(T element) {
    Set<T> result = Sets.newIdentityHashSet();
    result.add(element);
    return result;
  }

  public static <T> Set<T> newIdentityHashSet(Iterable<T> c) {
    Set<T> result = Sets.newIdentityHashSet();
    c.forEach(result::add);
    return result;
  }
}
