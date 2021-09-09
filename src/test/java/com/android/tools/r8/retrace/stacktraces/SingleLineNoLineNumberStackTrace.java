// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class SingleLineNoLineNumberStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat foo.a.a(Unknown Source)",
        "\tat foo.a.b(Unknown Source)",
        "\tat foo.a.c(Unknown Source)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> foo.a:",
        "    0:0:void method1(java.lang.String):42:42 -> a",
        "    0:0:void main(java.lang.String[]):28 -> a",
        "    0:0:void method2(java.lang.String):42:48 -> b",
        "    0:0:void main2(java.lang.String[]):28 -> b",
        "    void main3(java.lang.String[]):153 -> c");
  }

  @Override
  public List<String> retracedStackTrace() {
    // TODO(b/191513686): Should have line-numbers for main, method1, main2 and main3.
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.method1(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.method2(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.main2(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.main3(Main.java)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
