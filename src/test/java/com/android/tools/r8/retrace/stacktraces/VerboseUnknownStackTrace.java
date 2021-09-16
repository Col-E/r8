// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import java.util.Arrays;
import java.util.List;

public class VerboseUnknownStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException", "\tat java.util.ArrayList.get(ArrayList.java:411)");
  }

  @Override
  public String mapping() {
    return "";
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException", "\tat java.util.ArrayList.get(ArrayList.java:411)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "java.lang.IndexOutOfBoundsException", "\tat java.util.ArrayList.get(ArrayList.java:411)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
