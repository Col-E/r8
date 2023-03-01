// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class PreambleLineNumberStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "  at kotlin.t.a(SourceFile)",
        "  at kotlin.t.a(SourceFile:0)",
        "  at kotlin.t.a(SourceFile:1)",
        "  at kotlin.t.a(SourceFile:2)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "kotlin.ResultKt -> kotlin.t:",
        "  1:1:void createFailure(java.lang.Throwable):122:122 -> a",
        "  2:2:void createFailure(java.lang.Throwable):124:124 -> a");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "  at kotlin.ResultKt.createFailure(Result.kt)",
        // TODO(b/270593835): We should report kotlin.ResultKt.createFailure(Result.kt).
        "  at kotlin.ResultKt.a(Result.kt:0)",
        "  at kotlin.ResultKt.createFailure(Result.kt:122)",
        "  at kotlin.ResultKt.createFailure(Result.kt:124)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        // TODO(b/270593835): We should not have an ambiguous frame reporting here.
        "  at kotlin.ResultKt.void createFailure(java.lang.Throwable)(Result.kt:122)",
        "  <OR> at kotlin.ResultKt.void createFailure(java.lang.Throwable)(Result.kt:124)",
        // TODO(b/270593835): We should report kotlin.ResultKt.createFailure(Result.kt).
        "  at kotlin.ResultKt.a(Result.kt:0)",
        "  at kotlin.ResultKt.void createFailure(java.lang.Throwable)(Result.kt:122)",
        "  at kotlin.ResultKt.void createFailure(java.lang.Throwable)(Result.kt:124)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
