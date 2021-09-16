// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import java.util.Arrays;
import java.util.List;

public class ObfucatedExceptionClassStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "a.b.c: Problem when compiling program",
        "    at r8.main(App:800)",
        "Caused by: a.b.c: You have to write the program first",
        "    at r8.retrace(App:184)",
        "    ... 7 more");
  }

  @Override
  public String mapping() {
    return "foo.bar.baz -> a.b.c:";
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "foo.bar.baz: Problem when compiling program",
        "    at r8.main(App:800)",
        "Caused by: foo.bar.baz: You have to write the program first",
        "    at r8.retrace(App:184)",
        "    ... 7 more");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "foo.bar.baz: Problem when compiling program",
        "    at r8.main(App:800)",
        "Caused by: foo.bar.baz: You have to write the program first",
        "    at r8.retrace(App:184)",
        "    ... 7 more");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
