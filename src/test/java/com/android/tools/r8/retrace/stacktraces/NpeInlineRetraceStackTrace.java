// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class NpeInlineRetraceStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException", "\tat a.a(:4)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "# { id: 'com.android.tools.r8.mapping', version: '2.0' }",
        "some.Class -> a:",
        "  4:4:void other.Class():23:23 -> a",
        "  4:4:void caller(other.Class):7 -> a",
        "  # { id: 'com.android.tools.r8.rewriteFrame', "
            + "conditions: ['throws(Ljava/lang/NullPointerException;)'],  "
            + "actions: ['removeInnerFrames(1)'] }");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat some.Class.caller(Class.java:7)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "\tat some.Class.void caller(other.Class)(Class.java:7)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
