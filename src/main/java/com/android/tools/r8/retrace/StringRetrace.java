// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.retrace.internal.RetraceStackFrameResultWithContextImpl;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Specialized Retrace class for retracing string retraces, with special handling for appending
 * additional information into the strings, such as OR's for ambiguous lines.
 */
@KeepForApi
public class StringRetrace extends Retrace<String, StackTraceElementStringProxy> {

  StringRetrace(
      StackTraceLineParser<String, StackTraceElementStringProxy> stackTraceLineParser,
      MappingSupplier<?> mappingSupplier,
      DiagnosticsHandler diagnosticsHandler,
      boolean isVerbose) {
    super(stackTraceLineParser, mappingSupplier, diagnosticsHandler, isVerbose);
  }

  /**
   * Default entry point for creating a retracer designed for string input and output.
   *
   * @param command the command with information about creating the StringRetrace
   * @return a StringRetrace object
   */
  public static StringRetrace create(RetraceOptions command) {
    return create(
        command.getMappingSupplier(),
        command.getDiagnosticsHandler(),
        command.getRegularExpression(),
        command.isVerbose());
  }

  /**
   * Entry point for creating a retracer designed for string input and output where the mapping file
   * has already been parsed.
   *
   * @param mappingSupplier a supplier that can be used to construct a retracer
   * @param diagnosticsHandler a diagnosticshandler for emitting information
   * @param regularExpression the regular expression to use for identifying information in strings
   * @param isVerbose specify to emit verbose information
   * @return a StringRetrace object
   */
  public static StringRetrace create(
      MappingSupplier<?> mappingSupplier,
      DiagnosticsHandler diagnosticsHandler,
      String regularExpression,
      boolean isVerbose) {
    return new StringRetrace(
        StackTraceLineParser.createRegularExpressionParser(regularExpression),
        mappingSupplier,
        diagnosticsHandler,
        isVerbose);
  }

  /**
   * Retraces a list of stack-trace lines and returns a list. Ambiguous and inline frames will be
   * appended automatically to the retraced string.
   *
   * @param stackTrace the incoming stack trace
   * @param context The context to retrace the stack trace in
   * @return the retraced stack trace
   */
  public RetraceStackFrameResultWithContext<String> retrace(
      List<String> stackTrace, RetraceStackTraceContext context) {
    RetraceStackTraceResult<String> result = retraceStackTrace(stackTrace, context);
    return RetraceStackFrameResultWithContextImpl.create(
        joinAmbiguousLines(result.getResult()), result.getContext());
  }

  /**
   * Retraces a list of parsed stack trace lines and returns a list. Ambiguous and inline frames
   * will be appended automatically to the retraced string.
   *
   * @param stackTrace the incoming parsed stack trace
   * @param context The context to retrace the stack trace in
   * @return the retraced stack trace
   */
  public RetraceStackFrameResultWithContext<String> retraceParsed(
      List<StackTraceElementStringProxy> stackTrace, RetraceStackTraceContext context) {
    RetraceStackTraceResult<String> result = retraceStackTraceParsed(stackTrace, context);
    return RetraceStackFrameResultWithContextImpl.create(
        joinAmbiguousLines(result.getResult()), result.getContext());
  }

  /**
   * Retraces a single stack trace line and returns the potential list of original frames
   *
   * @param stackTraceLine the stack trace line to retrace
   * @param context The context to retrace the stack trace in
   * @return the retraced frames
   */
  public RetraceStackFrameResultWithContext<String> retrace(
      String stackTraceLine, RetraceStackTraceContext context) {
    RetraceStackFrameAmbiguousResultWithContext<String> listRetraceStackTraceResult =
        retraceFrame(stackTraceLine, context);
    return RetraceStackFrameResultWithContextImpl.create(
        joinAmbiguousLines(Collections.singletonList(listRetraceStackTraceResult)),
        listRetraceStackTraceResult.getContext());
  }

  /**
   * Processes supplied strings and calls lineConsumer with retraced strings in a streaming way
   *
   * @param lineSupplier the supplier of strings with returning null as terminator
   * @param lineConsumer the consumer of retraced strings
   */
  public <E extends Throwable> void retraceSupplier(
      StreamSupplier<E> lineSupplier, Consumer<String> lineConsumer) throws E {
    RetraceStackTraceContext context = RetraceStackTraceContext.empty();
    String retraceLine;
    while ((retraceLine = lineSupplier.getNext()) != null) {
      RetraceStackFrameResultWithContext<String> result = retrace(retraceLine, context);
      context = result.getContext();
      result.forEach(lineConsumer);
    }
  }

  private List<String> joinAmbiguousLines(
      List<RetraceStackFrameAmbiguousResult<String>> retracedResult) {
    List<String> result = new ArrayList<>();
    for (RetraceStackFrameAmbiguousResult<String> ambiguousResult : retracedResult) {
      boolean addedLines = true;
      int lineIndex = 0;
      while (addedLines) {
        addedLines = false;
        Set<String> reportedFrames = new HashSet<>();
        RetraceStackFrameResult<String> firstResult = null;
        for (RetraceStackFrameResult<String> inlineFrames : ambiguousResult.getAmbiguousResult()) {
          if (firstResult == null) {
            firstResult = inlineFrames;
          }
          if (lineIndex < inlineFrames.size()) {
            addedLines = true;
            String frameToAdd = inlineFrames.get(lineIndex);
            if (reportedFrames.add(frameToAdd)) {
              boolean isAmbiguous = inlineFrames != firstResult;
              if (isAmbiguous) {
                result.add(insertOrIntoStackTraceLine(frameToAdd));
              } else {
                result.add(frameToAdd);
              }
            }
          }
        }
        lineIndex += 1;
      }
    }
    return result;
  }

  private String insertOrIntoStackTraceLine(String stackTraceLine) {
    // We are reporting an ambiguous frame. To support retracing tools that
    // retrace line by line we have to emit <OR> at the point of the first 'at '
    // if we can find it.
    int indexToInsertOr = stackTraceLine.indexOf("at ");
    if (indexToInsertOr < 0) {
      indexToInsertOr = Math.max(StringUtils.firstNonWhitespaceCharacter(stackTraceLine), 0);
    }
    return stackTraceLine.substring(0, indexToInsertOr)
        + "<OR> "
        + stackTraceLine.substring(indexToInsertOr);
  }
}
