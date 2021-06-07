// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.google.common.base.Suppliers;
import java.util.function.Supplier;

public class SupplierUtils {

  public static <T> Supplier<T> nonThreadSafeMemoize(Supplier<T> supplier) {
    Box<T> box = new Box<>();
    return () -> box.computeIfAbsent(supplier);
  }

  public static <T, E extends Throwable> Supplier<T> memoize(ThrowingSupplier<T, E> supplier) {
    return Suppliers.memoize(
        () -> {
          try {
            return supplier.get();
          } catch (Throwable e) {
            throw new RuntimeException(e);
          }
        });
  }
}
