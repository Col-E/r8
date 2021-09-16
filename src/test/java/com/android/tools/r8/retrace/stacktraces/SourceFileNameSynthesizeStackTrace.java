// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class SourceFileNameSynthesizeStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "\tat mapping.a(AW779999992:21)",
        "\tat noMappingKt.noMapping(AW779999992:21)",
        "\tat mappingKotlin.b(AW779999992:21)");
  }

  @Override
  public String mapping() {
    return StringUtils.joinLines(
        "android.support.v7.widget.ActionMenuView -> mapping:",
        "  21:21:void invokeItem():624 -> a",
        "android.support.v7.widget.ActionMenuViewKt -> mappingKotlin:",
        "  21:21:void invokeItem():624 -> b");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "\tat android.support.v7.widget.ActionMenuView.invokeItem(ActionMenuView.java:624)",
        "\tat noMappingKt.noMapping(AW779999992:21)",
        "\tat android.support.v7.widget.ActionMenuViewKt.invokeItem(ActionMenuView.kt:624)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "\tat android.support.v7.widget.ActionMenuView.void invokeItem()(ActionMenuView.java:624)",
        "\tat noMappingKt.noMapping(AW779999992:21)",
        "\tat android.support.v7.widget.ActionMenuViewKt.void invokeItem()(ActionMenuView.kt:624)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
