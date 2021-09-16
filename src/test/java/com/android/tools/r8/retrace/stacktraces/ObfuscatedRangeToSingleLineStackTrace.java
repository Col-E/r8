// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class ObfuscatedRangeToSingleLineStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "UnknownException: This is just a fake exception",
        "  at a.a(:8)",
        "  at a.a(:13)",
        "  at a.b(:1399)");
  }

  @Override
  public String mapping() {
    return StringUtils.lines(
        "foo.bar.Baz -> a:",
        "  1:10:void qux():27:27 -> a",
        "  11:15:void qux():42 -> a",
        "  1337:1400:void foo.bar.Baz.quux():113:113 -> b",
        "  1337:1400:void quuz():72 -> b");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "UnknownException: This is just a fake exception",
        "  at foo.bar.Baz.qux(Baz.java:27)",
        "  at foo.bar.Baz.qux(Baz.java:42)",
        "  at foo.bar.Baz.quux(Baz.java:113)",
        "  at foo.bar.Baz.quuz(Baz.java:72)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "UnknownException: This is just a fake exception",
        "  at foo.bar.Baz.void qux()(Baz.java:27)",
        "  at foo.bar.Baz.void qux()(Baz.java:42)",
        "  at foo.bar.Baz.void quux()(Baz.java:113)",
        "  at foo.bar.Baz.void quuz()(Baz.java:72)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
