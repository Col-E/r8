// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class NoObfuscationRangeMappingWithStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat foo.a(Bar.dummy:0)",
        "\tat foo.b(Foo.dummy:2)",
        "\tat foo.c(Baz.dummy:8)",
        "\tat foo.d(Qux.dummy:7)");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.foo(Main.java:1)",
        "\tat com.android.tools.r8.naming.retrace.Main.bar(Main.java:3)",
        "\tat com.android.tools.r8.naming.retrace.Main.baz(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.void foo(long)(Main.java:1)",
        "\tat com.android.tools.r8.naming.retrace.Main.void bar(int)(Main.java:3)",
        "\tat com.android.tools.r8.naming.retrace.Main.void baz()(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.void main(java.lang.String[])(Main.java)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> foo:",
        "    void foo(long):1:1 -> a",
        "    void bar(int):3 -> b",
        "    void baz():0:0 -> c", // For 0:0 and 0 use the original line number
        "    void main(java.lang.String[]):0 -> d");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
