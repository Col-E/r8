// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.LinkedHashSet;

public class LinkedHashSetUtils {

  public static <T> void addAll(LinkedHashSet<T> set, LinkedHashSet<T> elements) {
    set.addAll(elements);
  }
}
