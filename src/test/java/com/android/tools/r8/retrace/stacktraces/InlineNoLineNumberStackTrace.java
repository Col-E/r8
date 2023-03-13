// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class InlineNoLineNumberStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.main(:3)");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.method3(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.method2(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.method1(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.void method3(long)(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.void method2(int)(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.void method1(java.lang.String)(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.void main(java.lang.String[])(Main.java)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> com.android.tools.r8.naming.retrace.Main:",
        "    1:1:void method1(java.lang.String):0:0 -> main",
        "    1:1:void main(java.lang.String[]):0 -> main",
        "    2:2:void method2(int):0:0 -> main",
        "    2:2:void method1(java.lang.String):0 -> main",
        "    2:2:void main(java.lang.String[]):0 -> main",
        "    3:3:void method3(long):0:0 -> main",
        "    3:3:void method2(int):0 -> main",
        "    3:3:void method1(java.lang.String):0 -> main",
        "    3:3:void main(java.lang.String[]):0 -> main");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
