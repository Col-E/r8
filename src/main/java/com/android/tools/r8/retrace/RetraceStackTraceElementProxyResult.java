// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.stream.Stream;

@KeepForApi
public interface RetraceStackTraceElementProxyResult<T, ST extends StackTraceElementProxy<T, ST>> {

  Stream<? extends RetraceStackTraceElementProxy<T, ST>> stream();

  /**
   * If the stream is empty, use getResultContext to obtain the resulting stack trace context. Due
   * to the lazyness of streams the result is only populated after querying the stream.
   *
   * @return the resulting stack trace context.
   */
  RetraceStackTraceContext getResultContext();
}
