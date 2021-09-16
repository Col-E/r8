// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import java.util.Arrays;
import java.util.List;

public class InvalidStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "  hulubulu",
        "  XXX, where are you",
        "a.b.c: Problem when compiling program",
        " . . . 7 more",
        "  ... 7 more");
  }

  @Override
  public String mapping() {
    return "foo.bar.baz -> a.b.c:";
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "  hulubulu",
        "  XXX, where are you",
        "foo.bar.baz: Problem when compiling program",
        " . . . 7 more",
        "  ... 7 more");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "  hulubulu",
        "  XXX, where are you",
        "foo.bar.baz: Problem when compiling program",
        " . . . 7 more",
        "  ... 7 more");
  }

  @Override
  public int expectedWarnings() {
    return 1;
  }
}
