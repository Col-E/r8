// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class InlineFileNameStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.dummy:3)");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat foo.Bar$Baz.baz(Bar.java)",
        "\tat Foo$Bar.bar(Foo.java:2)",
        "\tat com.android.tools.r8.naming.retrace.Main$Foo.method1(Main.java:8)",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java:7)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat foo.Bar$Baz.void baz(long)(Bar.java)",
        "\tat Foo$Bar.void bar(int)(Foo.java:2)",
        "\tat com.android.tools.r8.naming.retrace.Main$Foo"
            + ".void method1(java.lang.String)(Main.java:8)",
        "\tat com.android.tools.r8.naming.retrace.Main.void main(java.lang.String[])(Main.java:7)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> com.android.tools.r8.naming.retrace.Main:",
        "    3:3:void foo.Bar$Baz.baz(long):0:0 -> main",
        "    3:3:void Foo$Bar.bar(int):2 -> main",
        "    3:3:void com.android.tools.r8.naming.retrace.Main$Foo.method1(java.lang.String):8 ->"
            + " main",
        "    3:3:void main(java.lang.String[]):7 -> main");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
