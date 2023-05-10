// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class AmbiguousInlineFramesStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "com.android.tools.r8.CompilationException:", "    at a.a.a(Unknown Source:1)");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "com.android.tools.r8.CompilationException:",
        "    at com.android.tools.r8.R8.foo(R8.java:42)",
        "    <OR> at com.android.tools.r8.R8.foo(R8.java:43)",
        "    <OR> at com.android.tools.r8.R8.foo(R8.java:44)",
        "    at com.android.tools.r8.R8.bar(R8.java:32)",
        "    at com.android.tools.r8.R8.baz(R8.java:10)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "com.android.tools.r8.CompilationException:",
        "    at com.android.tools.r8.R8.void foo(int)(R8.java:42)",
        "    <OR> at com.android.tools.r8.R8.void foo(int)(R8.java:43)",
        "    <OR> at com.android.tools.r8.R8.void foo(int)(R8.java:44)",
        "    at com.android.tools.r8.R8.void bar(int,int)(R8.java:32)",
        "    at com.android.tools.r8.R8.void baz(int,int)(R8.java:10)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.R8 -> a.a:",
        "  1:1:void foo(int):42:44 -> a",
        "  1:1:void bar(int, int):32 -> a",
        "  1:1:void baz(int, int):10 -> a");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
