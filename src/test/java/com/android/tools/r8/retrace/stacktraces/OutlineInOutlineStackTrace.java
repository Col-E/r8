// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class OutlineInOutlineStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "java.io.IOException: INVALID_SENDER", "\tat a.a(:4)", "\tat b.a(:6)", "\tat c.a(:28)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "# { id: 'com.android.tools.r8.mapping', version: '2.0' }",
        "outline1.Class -> a:",
        "  4:4:int outline():0:0 -> a",
        "# { 'id':'com.android.tools.r8.outline' }",
        "outline2.Class -> b:",
        "  6:6:int outline():0:0 -> a",
        "# { 'id':'com.android.tools.r8.outlineCallsite', 'positions': { '4': 43 } }",
        "  42:43:int outline():0:0 -> a",
        "# { 'id':'com.android.tools.r8.outline' }",
        "some.Class -> c:",
        "  1:1:void foo.bar.Baz.qux():42:42 -> a",
        "  10:11:int outlineCaller(int):98:98 -> a",
        "  28:28:int outlineCaller(int):0:0 -> a",
        "# { 'id':'com.android.tools.r8.outlineCallsite', 'positions': { '42': 10, '43': 11 } }");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "java.io.IOException: INVALID_SENDER", "\tat some.Class.outlineCaller(Class.java:98)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "java.io.IOException: INVALID_SENDER",
        "\tat some.Class.int outlineCaller(int)(Class.java:98)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
