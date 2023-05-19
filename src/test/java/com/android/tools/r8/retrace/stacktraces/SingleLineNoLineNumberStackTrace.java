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
        "\tat foo.a.c(Unknown Source)",
        "\tat foo.a.d(Unknown Source)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> foo.a:",
        "    0:0:void method1(java.lang.String):42:42 -> a",
        "    0:0:void main(java.lang.String[]):28 -> a",
        "    0:0:void method2(java.lang.String):42:44 -> b",
        "    0:0:void main2(java.lang.String[]):29 -> b",
        "    void method3(java.lang.String):72:72 -> c",
        "    void main3(java.lang.String[]):30 -> c",
        "    void main4(java.lang.String[]):153 -> d");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.method1(Main.java:42)",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java:28)",
        "\tat com.android.tools.r8.naming.retrace.Main.method2(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.main2(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.main3(Main.java)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.method3(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.main4(Main.java:153)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.void"
            + " method1(java.lang.String)(Main.java:42)",
        "\tat com.android.tools.r8.naming.retrace.Main.void main(java.lang.String[])(Main.java:28)",
        "\tat com.android.tools.r8.naming.retrace.Main.void"
            + " method2(java.lang.String)(Main.java:42)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.void"
            + " method2(java.lang.String)(Main.java:43)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.void"
            + " method2(java.lang.String)(Main.java:44)",
        "\tat com.android.tools.r8.naming.retrace.Main.void"
            + " main2(java.lang.String[])(Main.java:29)",
        "\tat com.android.tools.r8.naming.retrace.Main.void"
            + " main3(java.lang.String[])(Main.java:30)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.void"
            + " method3(java.lang.String)(Main.java:72)",
        "\tat com.android.tools.r8.naming.retrace.Main.void"
            + " main4(java.lang.String[])(Main.java:153)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
