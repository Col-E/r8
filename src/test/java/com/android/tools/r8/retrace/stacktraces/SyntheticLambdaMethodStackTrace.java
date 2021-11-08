// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class SyntheticLambdaMethodStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "  at a.a.a(a.java:5)",
        "  at a.b.a(Unknown Source)",
        "  at a.a.b(a.java:3)",
        "  at a.a.c(a.java:2)",
        "  at example.Main.main(Main.java:1)");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "  at example.Foo.lambda$main$0(Foo.java:225)",
        "  at example.Foo.runIt(Foo.java:218)",
        "  at example.Foo.main(Foo.java:223)",
        "  at example.Main.main(Main.java:123)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "Exception in thread \"main\" java.lang.NullPointerException",
        "  at example.Foo.void lambda$main$0()(Foo.java:225)",
        "  at example.Foo.void runIt()(Foo.java:218)",
        "  at example.Foo.void main()(Foo.java:223)",
        "  at example.Main.void main(java.lang.String[])(Main.java:123)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "# {'id':'com.android.tools.r8.mapping','version':'1.0'}",
        "example.Main -> example.Main:",
        "  1:1:void main(java.lang.String[]):123 -> main",
        "example.Foo -> a.a:",
        "  5:5:void lambda$main$0():225 -> a",
        "  3:3:void runIt():218 -> b",
        "  2:2:void main():223 -> c",
        "example.Foo$$ExternalSyntheticLambda0 -> a.b:",
        "  void run(example.Foo) -> a",
        "    # {'id':'com.android.tools.r8.synthesized'}");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
