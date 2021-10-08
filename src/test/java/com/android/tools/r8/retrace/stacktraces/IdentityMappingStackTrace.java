// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class IdentityMappingStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException", "\tat a.a(:10)", "\tat b.a(:11)", "\tat c.a(:12)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.One -> a:",
        "  10:10:void foo(int) -> a",
        "com.android.tools.r8.Other -> b:",
        "  11:11:void bar(int, int) -> a", // This is an inline frame
        "  11:11:boolean baz(int, int) -> a",
        "com.android.tools.r8.Third -> c:",
        "  12:12:void qux(int) -> a", // This is also an inline frame
        "  12:12:void other(int, int) -> b",
        "  12:12:boolean quux(int, int) -> a");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException",
        "\tat com.android.tools.r8.One.foo(One.java:10)",
        "\tat com.android.tools.r8.Other.bar(Other.java:11)",
        "\tat com.android.tools.r8.Other.baz(Other.java:11)",
        "\tat com.android.tools.r8.Third.qux(Third.java:12)",
        "\tat com.android.tools.r8.Third.quux(Third.java:12)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException",
        "\tat com.android.tools.r8.One.void foo(int)(One.java:10)",
        "\tat com.android.tools.r8.Other.void bar(int,int)(Other.java:11)",
        "\tat com.android.tools.r8.Other.boolean baz(int,int)(Other.java:11)",
        "\tat com.android.tools.r8.Third.void qux(int)(Third.java:12)",
        "\tat com.android.tools.r8.Third.boolean quux(int,int)(Third.java:12)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
