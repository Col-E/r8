// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.RetraceStackFrameAmbiguousResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceStackTraceResult;
import java.util.List;
import java.util.function.Consumer;

public class RetraceStackTraceResultImpl<T> implements RetraceStackTraceResult<T> {

  private final List<RetraceStackFrameAmbiguousResult<T>> result;
  private final RetraceStackTraceContext context;

  private RetraceStackTraceResultImpl(
      List<RetraceStackFrameAmbiguousResult<T>> result, RetraceStackTraceContext context) {
    this.result = result;
    this.context = context;
  }

  public static <T> RetraceStackTraceResult<T> create(
      List<RetraceStackFrameAmbiguousResult<T>> result, RetraceStackTraceContext context) {
    return new RetraceStackTraceResultImpl<>(result, context);
  }

  @Override
  public RetraceStackTraceContext getContext() {
    return context;
  }

  @Override
  public List<RetraceStackFrameAmbiguousResult<T>> getResult() {
    return result;
  }

  @Override
  public void forEach(Consumer<RetraceStackFrameAmbiguousResult<T>> consumer) {
    getResult().forEach(consumer);
  }

  @Override
  public boolean isEmpty() {
    return result.isEmpty();
  }
}
