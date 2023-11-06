// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.retrace.internal.RetraceBase;
import java.util.List;

/** An async retrace tool for obfuscated stack traces. */
@KeepForApi
public class RetraceAsync<T, ST extends StackTraceElementProxy<T, ST>> extends RetraceBase<T, ST> {

  private final MappingSupplierAsync<?> mappingSupplier;
  private final DiagnosticsHandler diagnosticsHandler;

  RetraceAsync(
      StackTraceLineParser<T, ST> stackTraceLineParser,
      MappingSupplierAsync<?> mappingSupplier,
      DiagnosticsHandler diagnosticsHandler,
      boolean isVerbose) {
    super(stackTraceLineParser, mappingSupplier, diagnosticsHandler, isVerbose);
    this.mappingSupplier = mappingSupplier;
    this.diagnosticsHandler = diagnosticsHandler;
  }

  public static <T, ST extends StackTraceElementProxy<T, ST>>
      RetraceAsync.Builder<T, ST> builder() {
    return new Builder<>();
  }

  /**
   * Retraces a complete stack frame and returns a list of retraced stack traces.
   *
   * @param stackTrace the stack trace to be retrace
   * @param context The context to retrace the stack trace in
   * @return list of potentially ambiguous stack traces.
   */
  public RetraceAsyncResult<RetraceStackTraceResult<T>> retraceStackTrace(
      List<T> stackTrace, RetraceStackTraceContext context) {
    return retraceStackTraceParsed(parse(stackTrace), context);
  }

  /**
   * Retraces a complete stack frame and returns a list of retraced stack traces.
   *
   * @param stackTrace the stack trace to be retrace
   * @param context The context to retrace the stack trace in
   * @return list of potentially ambiguous stack traces.
   */
  public RetraceAsyncResult<RetraceStackTraceResult<T>> retraceStackTraceParsed(
      List<ST> stackTrace, RetraceStackTraceContext context) {
    registerUses(stackTrace);
    return partitionSupplier ->
        retraceStackTraceParsedWithRetracer(
            mappingSupplier.createRetracer(diagnosticsHandler, partitionSupplier),
            stackTrace,
            context);
  }

  /**
   * Retraces a stack trace frame with support for splitting up ambiguous results.
   *
   * @param stackTraceFrame The frame to retrace that can give rise to ambiguous results
   * @param context The context to retrace the stack trace in
   * @return A collection of retraced frame where each entry in the outer list is ambiguous
   */
  public RetraceAsyncResult<RetraceStackFrameAmbiguousResultWithContext<T>> retraceFrame(
      T stackTraceFrame, RetraceStackTraceContext context) {
    ST parsedFrame = parse(stackTraceFrame);
    registerUses(parsedFrame);
    return partitionSupplier ->
        retraceFrameWithRetracer(
            mappingSupplier.createRetracer(diagnosticsHandler, partitionSupplier),
            parsedFrame,
            context);
  }

  /**
   * Utility method for tracing a single line that also retraces ambiguous lines without being able
   * to distinguish them. For retracing with ambiguous results separated, use {@link #retraceFrame}
   *
   * @param stackTraceLine the stack trace line to retrace
   * @param context The context to retrace the stack trace in
   * @return the retraced stack trace line
   */
  public RetraceAsyncResult<RetraceStackFrameResultWithContext<T>> retraceLine(
      T stackTraceLine, RetraceStackTraceContext context) {
    ST parsedFrame = parse(stackTraceLine);
    registerUses(parsedFrame);
    return partitionSupplier ->
        retraceLineWithRetracer(
            mappingSupplier.createRetracer(diagnosticsHandler, partitionSupplier),
            parsedFrame,
            context);
  }

  @KeepForApi
  public static class Builder<T, ST extends StackTraceElementProxy<T, ST>>
      extends RetraceBuilderBase<Builder<T, ST>, T, ST> {

    private MappingSupplierAsync<?> mappingSupplier;

    @Override
    public RetraceAsync.Builder<T, ST> self() {
      return this;
    }

    public RetraceAsync.Builder<T, ST> setMappingSupplier(MappingSupplierAsync<?> mappingSupplier) {
      this.mappingSupplier = mappingSupplier;
      return self();
    }

    public RetraceAsync<T, ST> build() {
      return new RetraceAsync<>(
          stackTraceLineParser, mappingSupplier, diagnosticsHandler, isVerbose);
    }
  }
}
