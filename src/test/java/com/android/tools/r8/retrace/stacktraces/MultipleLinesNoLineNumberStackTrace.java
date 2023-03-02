// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class MultipleLinesNoLineNumberStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat foo.a.a(Unknown Source)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> foo.a:",
        "    0:0:void method1(java.lang.String):42:42 -> a",
        "    0:0:void main(java.lang.String[]):28 -> a",
        "    1:1:void main(java.lang.String[]):153 -> a");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.method1(Main.java:42)",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java:28)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.void"
            + " method1(java.lang.String)(Main.java:42)",
        "\tat com.android.tools.r8.naming.retrace.Main.void"
            + " main(java.lang.String[])(Main.java:28)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
