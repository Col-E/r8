// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.retrace.internal.RetraceUtils.firstNonWhiteSpaceCharacterFromIndex;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy;
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
   * Default entry point for creating a retrace designed for string input and output.
   *
   * @param command the command with information about creating the StringRetrace
   * @return a StringRetrace object
   */
  public static StringRetrace create(RetraceOptions command) {
    Retracer retracer =
        Retracer.createDefault(command.getProguardMapProducer(), command.getDiagnosticsHandler());
    return new StringRetrace(
        StackTraceLineParser.createRegularExpressionParser(command.getRegularExpression()),
        StackTraceElementProxyRetracer.createDefault(retracer),
        command.getDiagnosticsHandler(),
        command.isVerbose());
  }

  /**
   * Retraces a list of stack-traces strings and returns a list. Ambiguous and inline frames will be
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
    assert !retracedResult.isEmpty();
    List<String> initialResult = retracedResult.get(0);
    initialResult.forEach(joinedConsumer);
    if (retracedResult.size() <= 1) {
      // The result is not ambiguous.
      return;
    }
    Set<String> reportedFrames = new HashSet<>(initialResult);
    for (int i = 1; i < retracedResult.size(); i++) {
      List<String> ambiguousResult = retracedResult.get(i);
      assert !ambiguousResult.isEmpty();
      String topFrame = ambiguousResult.get(0);
      if (reportedFrames.add(topFrame)) {
        ambiguousResult.forEach(
            retracedString -> {
              int firstCharIndex = firstNonWhiteSpaceCharacterFromIndex(retracedString, 0);
              retracedString =
                  retracedString.substring(0, firstCharIndex)
                      + "<OR> "
                      + retracedString.substring(firstCharIndex);
              joinedConsumer.accept(retracedString);
            });
      }
    }
  }
}
