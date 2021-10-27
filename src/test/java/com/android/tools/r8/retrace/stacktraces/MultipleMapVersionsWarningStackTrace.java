// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class MultipleMapVersionsWarningStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException", "\tat a.a(:4)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "# { id: 'com.android.tools.r8.mapping', version: '98.0' }",
        "some.Class -> a:",
        "# { id: 'com.android.tools.r8.mapping', version: '99.0' }",
        "some.other.Class -> b:",
        "# { id: 'com.android.tools.r8.mapping', version: '1.0' }",
        "some.third.Class -> c:");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat some.Class.a(Class.java:4)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat some.Class.a(Class.java:4)");
  }

  @Override
  public int expectedWarnings() {
    return 2;
  }
}
