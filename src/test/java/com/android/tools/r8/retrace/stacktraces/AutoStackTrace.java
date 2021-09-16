// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class AutoStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return ImmutableList.of(
        "java.io.IOException: INVALID_SENDER",
        "\tat qtr.a(:com.google.android.gms@203915081@20.39.15 (060808-335085812):46)",
        "\tat qtr.a(:com.google.android.gms@203915081@20.39.15 (060808-335085812):18)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.AutoTest -> qtr:",
        "  46:46:void foo(int):200:200 -> a",
        "  17:19:void foo(int,int):23:25 -> a");
  }

  @Override
  public List<String> retracedStackTrace() {
    return ImmutableList.of(
        "java.io.IOException: INVALID_SENDER",
        "\tat com.android.tools.r8.AutoTest.foo(AutoTest.java:200)",
        "\tat com.android.tools.r8.AutoTest.foo(AutoTest.java:24)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return ImmutableList.of(
        "java.io.IOException: INVALID_SENDER",
        "\tat com.android.tools.r8.AutoTest.void foo(int)(AutoTest.java:200)",
        "\tat com.android.tools.r8.AutoTest.void foo(int,int)(AutoTest.java:24)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
