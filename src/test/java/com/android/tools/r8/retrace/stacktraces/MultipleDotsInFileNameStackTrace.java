// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.google.common.collect.ImmutableList;
import java.util.List;

public class MultipleDotsInFileNameStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return ImmutableList.of("  at a.s(foo.bar.baz:1)");
  }

  @Override
  public String mapping() {
    return "some.Class -> a:\n"
        // Sourcefile metadata.
        + "# {\"id\":\"sourceFile\",\"fileName\":\"Class.kt\"}\n"
        + "    1:3:int strawberry(int):99:101 -> s\n"
        + "    4:5:int mango(float):121:122 -> s\n";
  }

  @Override
  public List<String> retracedStackTrace() {
    return ImmutableList.of("  at some.Class.strawberry(Class.kt:99)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return ImmutableList.of("  at some.Class.int strawberry(int)(Class.kt:99)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
