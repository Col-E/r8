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
        "\tat foo.Bar$Baz.baz(Bar.dummy:0)",
        "\tat Foo$Bar.bar(Foo.dummy:2)",
        "\tat com.android.tools.r8.naming.retrace.Main$Foo.method1(Main.dummy:8)",
        "\tat com.android.tools.r8.naming.retrace.Main.main(Main.dummy:7)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.naming.retrace.Main -> com.android.tools.r8.naming.retrace.Main:",
        "    3:3:void foo.Bar$Baz.baz(long):0:0 -> main",
        "    3:3:void Foo$Bar.bar(int):2 -> main",
        "    3:3:void com.android.tools.r8.naming.retrace.Main$Foo.method1(java.lang.String):8:8"
            + " -> main",
        "    3:3:void main(java.lang.String[]):7 -> main");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
