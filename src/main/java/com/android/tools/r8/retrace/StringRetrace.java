// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.retrace.internal.RetraceUtils.firstNonWhiteSpaceCharacterFromIndex;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.retrace.internal.ResultWithContextImpl;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Specialized Retrace class for retracing string retraces, with special handling for appending
 * additional information into the strings, such as OR's for ambiguous lines.
 */
@Keep
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
  public ResultWithContext<String> retrace(
      List<String> stackTrace, RetraceStackTraceContext context) {
    ResultWithContext<List<List<String>>> listResultWithContext =
        retraceStackTrace(stackTrace, context);
    List<String> retracedStrings = new ArrayList<>();
    for (List<List<String>> newLines : listResultWithContext.getLines()) {
      ListUtils.forEachWithIndex(
          newLines,
          (inlineFrames, ambiguousIndex) -> {
            for (int i = 0; i < inlineFrames.size(); i++) {
              String stackTraceLine = inlineFrames.get(i);
              if (i == 0 && ambiguousIndex > 0) {
                // We are reporting an ambiguous frame. To support retracing tools that retrace line
                // by line we have to emit <OR> at the point of the first 'at ' if we can find it.
                int indexToInsertOr = stackTraceLine.indexOf("at ");
                if (indexToInsertOr < 0) {
                  indexToInsertOr =
                      Math.max(StringUtils.firstNonWhitespaceCharacter(stackTraceLine), 0);
                }
                retracedStrings.add(
                    stackTraceLine.substring(0, indexToInsertOr)
                        + "<OR> "
                        + stackTraceLine.substring(indexToInsertOr));
              } else {
                retracedStrings.add(stackTraceLine);
              }
            }
          });
    }
    return ResultWithContextImpl.create(retracedStrings, listResultWithContext.getContext());
  }

  /**
   * Retraces a list of parsed stack trace lines and returns a list. Ambiguous and inline frames
   * will be appended automatically to the retraced string.
   *
   * @param stackTrace the incoming parsed stack trace
   * @param context The context to retrace the stack trace in
   * @return the retraced stack trace
   */
  public ResultWithContext<String> retraceParsed(
      List<StackTraceElementStringProxy> stackTrace, RetraceStackTraceContext context) {
    ResultWithContext<List<List<String>>> listResultWithContext =
        retraceStackTraceParsed(stackTrace, context);
    List<String> retracedStrings = new ArrayList<>();
    for (List<List<String>> newLines : listResultWithContext.getLines()) {
      ListUtils.forEachWithIndex(
          newLines,
          (inlineFrames, ambiguousIndex) -> {
            for (int i = 0; i < inlineFrames.size(); i++) {
              String stackTraceLine = inlineFrames.get(i);
              if (i == 0 && ambiguousIndex > 0) {
                // We are reporting an ambiguous frame. To support retracing tools that retrace line
                // by line we have to emit <OR> at the point of the first 'at ' if we can find it.
                int indexToInsertOr = stackTraceLine.indexOf("at ");
                if (indexToInsertOr < 0) {
                  indexToInsertOr =
                      Math.max(StringUtils.firstNonWhitespaceCharacter(stackTraceLine), 0);
                }
                retracedStrings.add(
                    stackTraceLine.substring(0, indexToInsertOr)
                        + "<OR> "
                        + stackTraceLine.substring(indexToInsertOr));
              } else {
                retracedStrings.add(stackTraceLine);
              }
            }
          });
    }
    return ResultWithContextImpl.create(retracedStrings, listResultWithContext.getContext());
  }

  /**
   * Retraces a single stack trace line and returns the potential list of original frames
   *
   * @param stackTraceLine the stack trace line to retrace
   * @param context The context to retrace the stack trace in
   * @return the retraced frames
   */
  public ResultWithContext<String> retrace(
      String stackTraceLine, RetraceStackTraceContext context) {
    ResultWithContext<List<String>> listResultWithContext = retraceFrame(stackTraceLine, context);
    List<String> result = new ArrayList<>();
    joinAmbiguousLines(listResultWithContext.getLines(), result::add);
    return ResultWithContextImpl.create(result, listResultWithContext.getContext());
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
      ResultWithContext<String> result = retrace(retraceLine, context);
      context = result.getContext();
      result.forEach(lineConsumer);
    }
  }

  private void joinAmbiguousLines(
      List<List<String>> retracedResult, Consumer<String> joinedConsumer) {
    if (retracedResult.isEmpty()) {
      // The result is empty, likely it maps to compiler synthesized items.
      return;
    }
    Set<String> reportedFrames = new HashSet<>();
    ListUtils.forEachWithIndex(
        retracedResult,
        (potentialResults, index) -> {
          assert !potentialResults.isEmpty();
          // Check if we already reported position.
          if (reportedFrames.add(potentialResults.get(0))) {
            boolean isAmbiguous = potentialResults != retracedResult.get(0);
            potentialResults.forEach(
                retracedString -> {
                  if (isAmbiguous) {
                    int firstCharIndex = firstNonWhiteSpaceCharacterFromIndex(retracedString, 0);
                    joinedConsumer.accept(
                        retracedString.substring(0, firstCharIndex)
                            + "<OR> "
                            + retracedString.substring(firstCharIndex));
                  } else {
                    joinedConsumer.accept(retracedString);
                  }
                });
          }
        });
  }
}
