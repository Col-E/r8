// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class InlineSourceFileStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList("  at b.c(Unknown Source:1)");
  }

  @Override
  public String mapping() {
    return StringUtils.joinLines(
        "foo.Bar -> a:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"SourceFile1.kt\"}",
        "foo.Baz -> b:",
        "# {\"id\":\"sourceFile\",\"fileName\":\"SourceFile2.kt\"}",
        "    1:1:void foo.Bar.method():22:22 -> c",
        "    1:1:void main(java.lang.String[]):32 -> c");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "  at foo.Bar.method(SourceFile1.kt:22)", "  at foo.Baz.main(SourceFile2.kt:32)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "  at foo.Bar.void method()(SourceFile1.kt:22)",
        "  at foo.Baz.void main(java.lang.String[])(SourceFile2.kt:32)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
