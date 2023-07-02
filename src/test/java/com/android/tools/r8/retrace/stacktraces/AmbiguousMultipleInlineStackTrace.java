// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class AmbiguousMultipleInlineStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException",
        "\tat com.android.tools.r8.Internal.zza(SourceFile:10)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.Internal -> com.android.tools.r8.Internal:",
        "  10:10:void some.inlinee1(int):10:10 -> zza",
        "  10:10:void foo(int):10 -> zza",
        "  11:12:void foo(int):11:12 -> zza",
        "  10:10:void some.inlinee2(int, int):20:20 -> zza",
        "  10:10:void foo(int, int):42 -> zza");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException",
        "\tat some.inlinee1(some.java:10)",
        "\t<OR> at some.inlinee2(some.java:20)",
        "\tat com.android.tools.r8.Internal.foo(Internal.java:10)",
        "\t<OR> at com.android.tools.r8.Internal.foo(Internal.java:42)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException",
        "\tat some.void inlinee1(int)(some.java:10)",
        "\t<OR> at some.void inlinee2(int,int)(some.java:20)",
        "\tat com.android.tools.r8.Internal.void foo(int)(Internal.java:10)",
        "\t<OR> at com.android.tools.r8.Internal.void foo(int,int)(Internal.java:42)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
