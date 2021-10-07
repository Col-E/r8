// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class ClassWithDashStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return ImmutableList.of(
        "java.lang.NullPointerException",
        "\tat I$-CC.staticMethod(I.java:66)",
        "\tat Main.main(Main.java:73)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "# {\"id\":\"com.android.tools.r8.mapping\",\"version\":\"1.0\"}",
        "Unused -> I$-CC:",
        "# {\"id\":\"com.android.tools.r8.synthesized\"}",
        "    66:66:void I.staticMethod() -> staticMethod",
        "    66:66:void staticMethod():0 -> staticMethod",
        "    # {\"id\":\"com.android.tools.r8.synthesized\"}");
  }

  @Override
  public List<String> retracedStackTrace() {
    return ImmutableList.of(
        "java.lang.NullPointerException",
        "\tat I.staticMethod(I.java:66)",
        "\tat Main.main(Main.java:73)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return ImmutableList.of(
        "java.lang.NullPointerException",
        "\tat I.void staticMethod()(I.java:66)",
        "\tat Main.main(Main.java:73)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
