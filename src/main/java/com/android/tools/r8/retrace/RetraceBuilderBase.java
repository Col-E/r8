// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;

@KeepForApi
public abstract class RetraceBuilderBase<
    B extends RetraceBuilderBase<B, T, ST>, T, ST extends StackTraceElementProxy<T, ST>> {

  protected StackTraceLineParser<T, ST> stackTraceLineParser;
  protected DiagnosticsHandler diagnosticsHandler;
  protected boolean isVerbose;

  public abstract B self();

  public B setStackTraceLineParser(StackTraceLineParser<T, ST> stackTraceLineParser) {
    this.stackTraceLineParser = stackTraceLineParser;
    return self();
  }

  public B setDiagnosticsHandler(DiagnosticsHandler diagnosticsHandler) {
    this.diagnosticsHandler = diagnosticsHandler;
    return self();
  }

  public B setVerbose(boolean isVerbose) {
    this.isVerbose = isVerbose;
    return self();
  }
}
