// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OverloadedWithAndWithoutRangeStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Collections.singletonList("  at A.a(SourceFile:3)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "some.Class -> A:",
        "  java.util.List select(java.util.List) -> a",
        "  3:3:void sync():425:425 -> a",
        "  void cancel(java.lang.String[]) -> a");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList("  at some.Class.sync(Class.java:425)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList("  at some.Class.void sync()(Class.java:425)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
