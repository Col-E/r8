// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class FileNameExtensionStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "a.b.c: Problem when compiling program",
        "    at R8.main(App:800)",
        "    at R8.main(Native Method)",
        "    at R8.main(Main.java:)",
        "    at R8.main(Main.kt:1)",
        "    at R8.main(Main.foo)",
        "    at R8.main()",
        "    at R8.main(Unknown)",
        "    at R8.main(SourceFile)",
        "    at R8.main(SourceFile:1)",
        "Suppressed: a.b.c: You have to write the program first",
        "    at R8.retrace(App:184)",
        "    ... 7 more");
  }

  @Override
  public String mapping() {
    return StringUtils.lines("foo.bar.baz -> a.b.c:", "R8 -> R8:");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "foo.bar.baz: Problem when compiling program",
        "    at R8.main(R8.java:800)",
        "    at R8.main(Native Method)",
        "    at R8.main(R8.java)",
        "    at R8.main(R8.kt:1)",
        "    at R8.main(R8.java)",
        "    at R8.main(R8.java)",
        "    at R8.main(R8.java)",
        "    at R8.main(R8.java)",
        "    at R8.main(R8.java:1)",
        "Suppressed: foo.bar.baz: You have to write the program first",
        "    at R8.retrace(R8.java:184)",
        "    ... 7 more");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "foo.bar.baz: Problem when compiling program",
        "    at R8.main(R8.java:800)",
        "    at R8.main(Native Method)",
        "    at R8.main(R8.java)",
        "    at R8.main(R8.kt:1)",
        "    at R8.main(R8.java)",
        "    at R8.main(R8.java)",
        "    at R8.main(R8.java)",
        "    at R8.main(R8.java)",
        "    at R8.main(R8.java:1)",
        "Suppressed: foo.bar.baz: You have to write the program first",
        "    at R8.retrace(R8.java:184)",
        "    ... 7 more");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
