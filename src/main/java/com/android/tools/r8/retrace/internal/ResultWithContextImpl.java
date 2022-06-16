// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.ResultWithContext;
import com.android.tools.r8.retrace.RetraceStackTraceContext;

public class ResultWithContextImpl<T> implements ResultWithContext<T> {

  private final T result;
  private final RetraceStackTraceContext context;

  private ResultWithContextImpl(T result, RetraceStackTraceContext context) {
    this.result = result;
    this.context = context;
  }

  public static <T> ResultWithContext<T> create(T result, RetraceStackTraceContext context) {
    return new ResultWithContextImpl<>(result, context);
  }

  @Override
  public RetraceStackTraceContext getContext() {
    return context;
  }

  @Override
  public T getResult() {
    return result;
  }
}
