// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class InlineWithLineNumbersStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.main(InliningRetraceTest.java:7)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> com.android.tools.r8.naming.retrace.Main:",
        "    1:1:void main(java.lang.String[]):101:101 -> main",
        "    2:4:void method1(java.lang.String):94:96 -> main",
        "    2:4:void main(java.lang.String[]):102 -> main",
        "    5:5:void method2(int):86:86 -> main",
        "    5:5:void method1(java.lang.String):96 -> main",
        "    5:5:void main(java.lang.String[]):102 -> main",
        "    6:7:void method3(long):80:81 -> main",
        "    6:7:void method2(int):88 -> main",
        "    6:7:void method1(java.lang.String):96 -> main",
        "    6:7:void main(java.lang.String[]):102 -> main");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.method3(Main.java:81)",
        "\tat com.android.tools.r8.naming.retrace.Main.method2(Main.java:88)",
        "\tat com.android.tools.r8.naming.retrace.Main.method1(Main.java:96)",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java:102)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.void method3(long)(Main.java:81)",
        "\tat com.android.tools.r8.naming.retrace.Main.void method2(int)(Main.java:88)",
        "\tat com.android.tools.r8.naming.retrace.Main."
            + "void method1(java.lang.String)(Main.java:96)",
        "\tat com.android.tools.r8.naming.retrace.Main."
            + "void main(java.lang.String[])(Main.java:102)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
