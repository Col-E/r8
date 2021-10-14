// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class UnknownSourceStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at a.a.a(Unknown Source)",
        "    at a.a.a(Unknown Source)",
        "    at com.android.tools.r8.R8.main(Unknown Source)",
        "Caused by: com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at a.a.a(Unknown Source)",
        "    ... 42 more");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at com.android.tools.r8.R8.bar(R8.java)",
        "    <OR> at com.android.tools.r8.R8.foo(R8.java)",
        "    at com.android.tools.r8.R8.bar(R8.java)",
        "    <OR> at com.android.tools.r8.R8.foo(R8.java)",
        "    at com.android.tools.r8.R8.main(Unknown Source)",
        "Caused by: com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at com.android.tools.r8.R8.bar(R8.java)",
        "    <OR> at com.android.tools.r8.R8.foo(R8.java)",
        "    ... 42 more");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at com.android.tools.r8.R8.void bar(int,int)(R8.java)",
        "    <OR> at com.android.tools.r8.R8.void foo(int)(R8.java)",
        "    at com.android.tools.r8.R8.void bar(int,int)(R8.java)",
        "    <OR> at com.android.tools.r8.R8.void foo(int)(R8.java)",
        "    at com.android.tools.r8.R8.main(Unknown Source)",
        "Caused by: com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at com.android.tools.r8.R8.void bar(int,int)(R8.java)",
        "    <OR> at com.android.tools.r8.R8.void foo(int)(R8.java)",
        "    ... 42 more");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.R8 -> a.a:", "  void foo(int) -> a", "  void bar(int, int) -> a");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
