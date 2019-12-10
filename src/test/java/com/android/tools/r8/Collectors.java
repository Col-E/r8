// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import java.util.stream.Collector;

public abstract class Collectors {

  public static <T> Collector<T, ?, T> toSingle() {
    return java.util.stream.Collectors.collectingAndThen(
        java.util.stream.Collectors.toList(),
        items -> {
          assert items.size() == 1;
          return items.get(0);
        });
  }
}
