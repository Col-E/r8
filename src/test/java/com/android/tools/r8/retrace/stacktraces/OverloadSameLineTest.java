// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class OverloadSameLineTest implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat foo.a.overload(Main.java:1)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> foo.a:",
        "    1:1:void overload():7:7 -> overload",
        "    1:1:void overload(java.lang.String):13:13 -> overload",
        "    1:1:void overload(int):15:15 -> overload");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.overload(Main.java:7)",
        "\tat com.android.tools.r8.naming.retrace.Main.overload(Main.java:13)",
        "\tat com.android.tools.r8.naming.retrace.Main.overload(Main.java:15)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.void overload()(Main.java:7)",
        "\tat com.android.tools.r8.naming.retrace.Main.void"
            + " overload(java.lang.String)(Main.java:13)",
        "\tat com.android.tools.r8.naming.retrace.Main.void overload(int)(Main.java:15)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
