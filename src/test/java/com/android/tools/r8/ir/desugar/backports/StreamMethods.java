// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.stream.Stream;

public class StreamMethods {
  public static <T> Stream<T> ofNullable(T t) {
    return t == null ? Stream.empty() : Stream.of(t);
  }
}
