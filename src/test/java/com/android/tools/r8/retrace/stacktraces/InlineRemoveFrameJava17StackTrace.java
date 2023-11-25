// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

/**
 * Manually throwing an NPE has a different syntax than it happening where there is no message
 * afterwards.
 */
public class InlineRemoveFrameJava17StackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "java.lang.NullPointerException", "\tat A.a(SourceFile:1)", "\tat A.main(SourceFile:1)");
  }

  @Override
  public String mapping() {
    return StringUtils.joinLines(
        "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
        "foo.Class -> A:",
        "    1:5:void inlinable():90:90 -> a",
        "    1:5:void caller():97 -> a",
        "      # {'id':'com.android.tools.r8.rewriteFrame',"
            + "'conditions':['throws(Ljava/lang/NullPointerException;)'],"
            + "'actions':['removeInnerFrames(1)']}",
        "    1:5:void outerCaller():107 -> a",
        "    1:1:void main():111:111 -> main");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "java.lang.NullPointerException",
        "\tat foo.Class.caller(Class.java:97)",
        "\tat foo.Class.outerCaller(Class.java:107)",
        "\tat foo.Class.main(Class.java:111)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "java.lang.NullPointerException",
        "\tat foo.Class.void caller()(Class.java:97)",
        "\tat foo.Class.void outerCaller()(Class.java:107)",
        "\tat foo.Class.void main()(Class.java:111)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
