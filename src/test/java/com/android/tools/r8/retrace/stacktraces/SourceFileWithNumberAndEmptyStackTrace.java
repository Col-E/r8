// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class SourceFileWithNumberAndEmptyStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "  at com.android.tools.r8.R8.a(R.java:34)", "  at com.android.tools.r8.R8.a(:34)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "com.android.tools.r8.R8 -> com.android.tools.r8.R8:",
        "  34:34:void com.android.tools.r8.utils.ExceptionUtils.withR8CompilationHandler("
            + "com.android.tools.r8.utils.Reporter,"
            + "com.android.tools.r8.utils.ExceptionUtils$CompileAction):59:59 -> a",
        "  34:34:void runForTesting(com.android.tools.r8.utils.AndroidApp,"
            + "com.android.tools.r8.utils.InternalOptions):261 -> a");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "  at com.android.tools.r8.utils.ExceptionUtils.withR8CompilationHandler("
            + "ExceptionUtils.java:59)",
        "  at com.android.tools.r8.R8.runForTesting(R8.java:261)",
        "  at com.android.tools.r8.utils.ExceptionUtils.withR8CompilationHandler("
            + "ExceptionUtils.java:59)",
        "  at com.android.tools.r8.R8.runForTesting(R8.java:261)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "  at com.android.tools.r8.utils.ExceptionUtils.void withR8CompilationHandler("
            + "com.android.tools.r8.utils.Reporter,"
            + "com.android.tools.r8.utils.ExceptionUtils$CompileAction)(ExceptionUtils.java:59)",
        "  at com.android.tools.r8.R8.void runForTesting("
            + "com.android.tools.r8.utils.AndroidApp,"
            + "com.android.tools.r8.utils.InternalOptions)(R8.java:261)",
        "  at com.android.tools.r8.utils.ExceptionUtils.void withR8CompilationHandler("
            + "com.android.tools.r8.utils.Reporter,"
            + "com.android.tools.r8.utils.ExceptionUtils$CompileAction)(ExceptionUtils.java:59)",
        "  at com.android.tools.r8.R8.void runForTesting("
            + "com.android.tools.r8.utils.AndroidApp,"
            + "com.android.tools.r8.utils.InternalOptions)(R8.java:261)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
