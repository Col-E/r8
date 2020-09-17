// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.function.Consumer;

public class ConsumerUtils {

  public static <T> Consumer<T> emptyConsumer() {
    return ignore -> {};
  }

  public static <T> ThrowingConsumer<T, RuntimeException> emptyThrowingConsumer() {
    return ignore -> {};
  }
}
