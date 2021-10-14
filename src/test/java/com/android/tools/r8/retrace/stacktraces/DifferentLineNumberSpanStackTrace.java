// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class DifferentLineNumberSpanStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat a.a(Unknown Source:1)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> a:",
        "  void method1(java.lang.String):42:44 -> a");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.method1(Main.java:42)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.method1(Main.java:43)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.method1(Main.java:44)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.void"
            + " method1(java.lang.String)(Main.java:42)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.void"
            + " method1(java.lang.String)(Main.java:43)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.void"
            + " method1(java.lang.String)(Main.java:44)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
