// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class TrailingWhitespaceStackTrace implements StackTraceForTest {

  private static final String NO_BREAKING_SPACE_STRING = new String(new char[] {160});

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat a.b.main(Main.dummy:1)" + NO_BREAKING_SPACE_STRING);
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java:7)"
            + NO_BREAKING_SPACE_STRING);
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.void main(java.lang.String[])(Main.java:7)"
            + NO_BREAKING_SPACE_STRING);
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> a.b:",
        "    1:1:void main(java.lang.String[]):7:7 -> main");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
