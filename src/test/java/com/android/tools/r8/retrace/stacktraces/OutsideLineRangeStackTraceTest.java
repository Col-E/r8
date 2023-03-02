// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class OutsideLineRangeStackTraceTest implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "java.io.IOException: INVALID_SENDER",
        "\tat a.a(:2)",
        "\tat a.a(Unknown Source)",
        "\tat b.a(:27)",
        "\tat b.a(Unknown Source)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "some.other.Class -> a:",
        "  void method1():42:42 -> a",
        "some.Class -> b:",
        "  1:3:void method2():11:13 -> a",
        "  4:4:void method2():10:10 -> a");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "java.io.IOException: INVALID_SENDER",
        "\tat some.other.Class.method1(Class.java:42)",
        "\tat some.other.Class.method1(Class.java:42)",
        "\tat some.Class.a(Class.java:27)",
        "\tat some.Class.method2(Class.java)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "java.io.IOException: INVALID_SENDER",
        "\tat some.other.Class.void method1()(Class.java:42)",
        "\tat some.other.Class.void method1()(Class.java:42)",
        "\tat some.Class.a(Class.java:27)",
        "\tat some.Class.void method2()(Class.java)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
