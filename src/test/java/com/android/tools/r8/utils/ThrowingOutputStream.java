// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.io.OutputStream;
import java.util.function.Supplier;

public class ThrowingOutputStream<T extends Error> extends OutputStream {

  private final Supplier<T> exceptionSupplier;

  public ThrowingOutputStream(Supplier<T> exceptionSupplier) {
    this.exceptionSupplier = exceptionSupplier;
  }

  @Override
  public void write(int b) {
    throw exceptionSupplier.get();
  }

  @Override
  public void write(byte[] b) {
    throw exceptionSupplier.get();
  }

  @Override
  public void write(byte[] b, int off, int len) {
    throw exceptionSupplier.get();
  }

  @Override
  public void flush() {
    throw exceptionSupplier.get();
  }

  @Override
  public void close() {
    throw exceptionSupplier.get();
  }
}
