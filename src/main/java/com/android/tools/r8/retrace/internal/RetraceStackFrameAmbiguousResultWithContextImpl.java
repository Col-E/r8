// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.RetraceStackFrameAmbiguousResultWithContext;
import com.android.tools.r8.retrace.RetraceStackFrameResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class RetraceStackFrameAmbiguousResultWithContextImpl<T>
    implements RetraceStackFrameAmbiguousResultWithContext<T> {

  private final RetraceStackTraceContext context;
  private final List<RetraceStackFrameResult<T>> result;

  private RetraceStackFrameAmbiguousResultWithContextImpl(
      List<RetraceStackFrameResult<T>> result, RetraceStackTraceContext context) {
    this.result = result;
    this.context = context;
  }

  public static <T> RetraceStackFrameAmbiguousResultWithContextImpl<T> create(
      List<RetraceStackFrameResult<T>> result, RetraceStackTraceContext context) {
    return new RetraceStackFrameAmbiguousResultWithContextImpl<>(result, context);
  }

  @Override
  public RetraceStackTraceContext getContext() {
    return context;
  }

  @Override
  public boolean isAmbiguous() {
    return result.size() > 1;
  }

  @Override
  public List<RetraceStackFrameResult<T>> getAmbiguousResult() {
    return result;
  }

  @Override
  public void forEach(Consumer<RetraceStackFrameResult<T>> consumer) {
    result.forEach(consumer);
  }

  @Override
  public void forEachWithIndex(BiConsumer<RetraceStackFrameResult<T>, Integer> consumer) {
    for (int i = 0; i < result.size(); i++) {
      consumer.accept(result.get(i), i);
    }
  }

  @Override
  public int size() {
    return result.size();
  }

  @Override
  public boolean isEmpty() {
    return result.isEmpty();
  }

  @Override
  public RetraceStackFrameResult<T> get(int i) {
    return result.get(i);
  }
}
