// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.retrace.internal.RetraceUtils.firstNonWhiteSpaceCharacterFromIndex;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy;
import com.android.tools.r8.utils.ListUtils;
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
      StackTraceElementProxyRetracer<String, StackTraceElementStringProxy> proxyRetracer,
      DiagnosticsHandler diagnosticsHandler,
      boolean isVerbose) {
    super(stackTraceLineParser, proxyRetracer, diagnosticsHandler, isVerbose);
  }

  /**
   * Default entry point for creating a retracer designed for string input and output.
   *
   * @param command the command with information about creating the StringRetrace
   * @return a StringRetrace object
   */
  public static StringRetrace create(RetraceOptions command) {
    return create(
        Retracer.createDefault(command.getProguardMapProducer(), command.getDiagnosticsHandler()),
        command.getDiagnosticsHandler(),
        command.getRegularExpression(),
        command.isVerbose());
  }

  /**
   * Entry point for creating a retracer designed for string input and output where the mapping file
   * has already been parsed.
   *
   * @param retracer a loaded retracer with parsed mapping
   * @param diagnosticsHandler a diagnosticshandler for emitting information
   * @param regularExpression the regular expression to use for identifying information in strings
   * @param isVerbose specify to emit verbose information
   * @return a StringRetrace object
   */
  public static StringRetrace create(
      Retracer retracer,
      DiagnosticsHandler diagnosticsHandler,
      String regularExpression,
      boolean isVerbose) {
    return new StringRetrace(
        StackTraceLineParser.createRegularExpressionParser(regularExpression),
        StackTraceElementProxyRetracer.createDefault(retracer),
        diagnosticsHandler,
        isVerbose);
  }

  /**
   * Retraces a list of stack-trace lines and returns a list. Ambiguous and inline frames will be
   * appended automatically to the retraced string.
   *
   * @param stackTrace the incoming stack trace
   * @return the retraced stack trace
   */
  public List<String> retrace(List<String> stackTrace) {
    List<String> retracedStrings = new ArrayList<>();
    retraceStackTrace(stackTrace, result -> joinAmbiguousLines(result, retracedStrings::add));
    return retracedStrings;
  }

  /**
   * Retraces a single stack trace line and returns the potential list of original frames
   *
   * @param stackTraceLine the stack trace line to retrace
   * @return the retraced frames
   */
  public List<String> retrace(String stackTraceLine) {
    List<String> result = new ArrayList<>();
    joinAmbiguousLines(retraceFrame(stackTraceLine), result::add);
    return result;
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
                            + "<OR #"
                            + (index)
                            + "> "
                            + retracedString.substring(firstCharIndex));
                  } else {
                    joinedConsumer.accept(retracedString);
                  }
                });
          }
        });
  }
}
