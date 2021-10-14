// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class NoObfuscatedLineNumberWithOverrideTest implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Unknown Source)",
        "\tat com.android.tools.r8.naming.retrace.Main.overload(Unknown Source)",
        "\tat com.android.tools.r8.naming.retrace.Main.definedOverload(Unknown Source)",
        "\tat com.android.tools.r8.naming.retrace.Main.mainPC(:3)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> com.android.tools.r8.naming.retrace.Main:",
        "    void main(java.lang.String):3 -> main",
        "    void definedOverload():7 -> definedOverload",
        "    void definedOverload(java.lang.String):11 -> definedOverload",
        "    void overload1():7 -> overload",
        "    void overload2(java.lang.String):11 -> overload",
        "    void mainPC(java.lang.String[]):42 -> mainPC");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java:3)",
        "\tat com.android.tools.r8.naming.retrace.Main.overload1(Main.java)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.overload2(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.definedOverload(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.mainPC(Main.java:42)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.void main(java.lang.String)(Main.java:3)",
        "\tat com.android.tools.r8.naming.retrace.Main.void overload1()(Main.java:7)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.void"
            + " overload2(java.lang.String)(Main.java:11)",
        "\tat com.android.tools.r8.naming.retrace.Main.void definedOverload()(Main.java:7)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.void"
            + " definedOverload(java.lang.String)(Main.java:11)",
        "\tat com.android.tools.r8.naming.retrace.Main.void"
            + " mainPC(java.lang.String[])(Main.java:42)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
