// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class AmbiguousMissingLineStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at a.a.a(Unknown Source:7)",
        "    at a.a.a(Unknown Source:8)",
        "    at com.android.tools.r8.R8.main(Unknown Source)",
        "Caused by: com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at a.a.a(Unknown Source:9)",
        "    ... 42 more");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at com.android.tools.r8.R8.bar(R8.java:7)",
        "    <OR> at com.android.tools.r8.R8.foo(R8.java:7)",
        "    at com.android.tools.r8.R8.bar(R8.java:8)",
        "    <OR> at com.android.tools.r8.R8.foo(R8.java:8)",
        "    at com.android.tools.r8.R8.main(Unknown Source)",
        "Caused by: com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at com.android.tools.r8.R8.bar(R8.java:9)",
        "    <OR> at com.android.tools.r8.R8.foo(R8.java:9)",
        "    ... 42 more");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at com.android.tools.r8.R8.void bar(int,int)(R8.java:7)",
        "    <OR> at com.android.tools.r8.R8.void foo(int)(R8.java:7)",
        "    at com.android.tools.r8.R8.void bar(int,int)(R8.java:8)",
        "    <OR> at com.android.tools.r8.R8.void foo(int)(R8.java:8)",
        "    at com.android.tools.r8.R8.main(Unknown Source)",
        "Caused by: com.android.tools.r8.CompilationException: foo[parens](Source:3)",
        "    at com.android.tools.r8.R8.void bar(int,int)(R8.java:9)",
        "    <OR> at com.android.tools.r8.R8.void foo(int)(R8.java:9)",
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
