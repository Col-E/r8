// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetraceStackTraceElementProxyResult;
import com.android.tools.r8.retrace.StackTraceElementProxy;
import com.android.tools.r8.retrace.internal.StackTraceElementProxyRetracerImpl.RetraceStackTraceElementProxyImpl;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class RetraceStackTraceElementProxyResultImpl<T, ST extends StackTraceElementProxy<T, ST>>
    implements RetraceStackTraceElementProxyResult<T, ST> {

  private final Stream<? extends RetraceStackTraceElementProxyImpl<T, ST>> resultStream;
  private final Supplier<RetraceStackTraceContext> resultContext;

  private RetraceStackTraceElementProxyResultImpl(
      Stream<? extends RetraceStackTraceElementProxyImpl<T, ST>> resultStream,
      Supplier<RetraceStackTraceContext> resultContext) {
    this.resultStream = resultStream;
    this.resultContext = resultContext;
  }

  @Override
  public Stream<? extends RetraceStackTraceElementProxyImpl<T, ST>> stream() {
    return resultStream;
  }

  @Override
  public RetraceStackTraceContext getResultContext() {
    return resultContext.get();
  }

  Builder<T, ST> builder() {
    return Builder.<T, ST>create().setResultStream(resultStream).setResultContext(resultContext);
  }

  static class Builder<T, ST extends StackTraceElementProxy<T, ST>> {

    Stream<? extends RetraceStackTraceElementProxyImpl<T, ST>> resultStream;
    Supplier<RetraceStackTraceContext> resultContext;

    private Builder() {}

    Builder<T, ST> setResultStream(
        Stream<? extends RetraceStackTraceElementProxyImpl<T, ST>> resultStream) {
      this.resultStream = resultStream;
      return this;
    }

    Builder<T, ST> setResultContext(Supplier<RetraceStackTraceContext> resultContext) {
      this.resultContext = resultContext;
      return this;
    }

    RetraceStackTraceElementProxyResultImpl<T, ST> build() {
      return new RetraceStackTraceElementProxyResultImpl<>(resultStream, resultContext);
    }

    static <T, ST extends StackTraceElementProxy<T, ST>> Builder<T, ST> create() {
      return new Builder<>();
    }
  }
}
