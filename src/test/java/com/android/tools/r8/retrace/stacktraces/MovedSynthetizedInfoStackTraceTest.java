// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class MovedSynthetizedInfoStackTraceTest implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "java.lang.RuntimeException: foobar", "\tat foo.bar.inlinee$synthetic(BaseCommand.java:2)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "# { id: 'com.android.tools.r8.mapping', version: '2.2' }",
        "com.android.tools.r8.BaseCommand$Builder -> foo.bar:",
        "    1:1:void inlinee(java.util.Collection):0:0 -> inlinee$synthetic",
        "    1:1:void inlinee$synthetic(java.util.Collection):0:0 -> inlinee$synthetic",
        "    2:2:void inlinee(java.util.Collection):206:206 -> inlinee$synthetic",
        "    2:2:void inlinee$synthetic(java.util.Collection):0:0 -> inlinee$synthetic",
        "      # {\"id\":\"com.android.tools.r8.synthesized\"}",
        "    4:4:void inlinee(java.util.Collection):208:208 -> inlinee$synthetic",
        "    4:4:void inlinee$synthetic(java.util.Collection):0 -> inlinee$synthetic",
        "    7:7:void error(origin.Origin,java.lang.Throwable):363:363 -> inlinee$synthetic",
        "    7:7:void inlinee(java.util.Collection):210 -> inlinee$synthetic",
        "    7:7:void inlinee$synthetic(java.util.Collection):0:0 -> inlinee$synthetic");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "java.lang.RuntimeException: foobar",
        "\tat com.android.tools.r8.BaseCommand$Builder.inlinee(BaseCommand.java:206)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "java.lang.RuntimeException: foobar",
        "\tat com.android.tools.r8.BaseCommand$Builder.void"
            + " inlinee(java.util.Collection)(BaseCommand.java:206)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
