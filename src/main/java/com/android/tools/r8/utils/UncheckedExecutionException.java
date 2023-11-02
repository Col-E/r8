// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.concurrent.ExecutionException;

public class UncheckedExecutionException extends RuntimeException {

  private final ExecutionException exception;

  public UncheckedExecutionException(ExecutionException exception) {
    super(exception);
    this.exception = exception;
  }

  public ExecutionException rethrow() throws ExecutionException {
    throw exception;
  }
}
