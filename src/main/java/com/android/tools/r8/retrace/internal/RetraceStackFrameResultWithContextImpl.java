// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.RetraceStackFrameResultWithContext;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import java.util.List;
import java.util.function.Consumer;

public class RetraceStackFrameResultWithContextImpl<T>
    implements RetraceStackFrameResultWithContext<T> {

  private final RetraceStackTraceContext context;
  private final List<T> result;

  private RetraceStackFrameResultWithContextImpl(List<T> result, RetraceStackTraceContext context) {
    this.result = result;
    this.context = context;
  }

  public static <T> RetraceStackFrameResultWithContextImpl<T> create(
      List<T> result, RetraceStackTraceContext context) {
    return new RetraceStackFrameResultWithContextImpl<>(result, context);
  }

  @Override
  public RetraceStackTraceContext getContext() {
    return context;
  }

  @Override
  public List<T> getResult() {
    return result;
  }

  @Override
  public void forEach(Consumer<T> consumer) {
    result.forEach(consumer);
  }

  @Override
  public int size() {
    return result.size();
  }

  @Override
  public T get(int i) {
    return result.get(i);
  }

  @Override
  public boolean isEmpty() {
    return result.isEmpty();
  }
}
