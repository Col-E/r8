// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class AmbiguousMethodVerboseStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat a.a.c(Foo.java)",
        "\tat a.a.b(Bar.java)",
        "\tat a.a.a(Baz.java)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> a.a:",
        "    com.android.Foo main(java.lang.String[],com.android.Bar) -> a",
        "    com.android.Foo main(java.lang.String[]) -> b",
        "    void main(com.android.Bar) -> b");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.c(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.java)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat com.android.tools.r8.naming.retrace.Main.c(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.com.android.Foo"
            + " main(java.lang.String[])(Main.java)",
        "\t<OR> at com.android.tools.r8.naming.retrace.Main.void main(com.android.Bar)(Main.java)",
        "\tat com.android.tools.r8.naming.retrace.Main.com.android.Foo"
            + " main(java.lang.String[],com.android.Bar)(Main.java)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
