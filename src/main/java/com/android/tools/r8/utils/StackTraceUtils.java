// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@KeepForApi
public class StackTraceUtils {

  private static final String pathToWriteStacktrace =
      System.getProperty("com.android.tools.r8.internalPathToStacktraces");

  private static final String SEPARATOR = "@@@@";

  private static final PrintStream printStream = getStacktracePrintStream();

  private static final int samplingInterval = getSamplingInterval();

  private static int getSamplingInterval() {
    String setSamplingInterval =
        System.getProperty("com.android.tools.r8.internalStackTraceSamplingInterval");
    if (setSamplingInterval == null) {
      return 1000;
    }
    return Integer.parseInt(setSamplingInterval);
  }

  private static int counter = 0;

  private static PrintStream getStacktracePrintStream() {
    if (pathToWriteStacktrace == null) {
      throw new RuntimeException("pathToWriteStacktrace is null");
    }
    try {
      return new PrintStream(pathToWriteStacktrace, StandardCharsets.UTF_8.name());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Utility function with the only purpose of being able to print stack traces from various
   * inserted points in R8. See RetraceStackTraceFunctionalCompositionTest.
   */
  public static void printCurrentStack(long identifier) {
    if (counter++ < samplingInterval) {
      new RuntimeException("------(" + identifier + "," + counter + ")------")
          .printStackTrace(printStream);
      printStream.println(SEPARATOR);
    }
  }
}
