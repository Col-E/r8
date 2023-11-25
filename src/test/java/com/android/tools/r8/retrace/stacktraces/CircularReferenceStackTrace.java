// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;

public class CircularReferenceStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Arrays.asList(
        "        [CIRCULAR REFERENCE: A.A]",
        " [CIRCULAR REFERENCE: A.B]",
        "        [CIRCULAR REFERENCE: None.existing.class]",
        "        [CIRCULAR REFERENCE: A.A] ",
        // Invalid Circular Reference lines.
        "        [CIRCU:AA]",
        "        [CIRCULAR REFERENCE: A.A",
        "        [CIRCULAR REFERENCE: ]",
        "        [CIRCULAR REFERENCE: None existing class]");
  }

  @Override
  public String mapping() {
    return StringUtils.lines("foo.bar.Baz -> A.A:", "foo.bar.Qux -> A.B:");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Arrays.asList(
        "        [CIRCULAR REFERENCE: foo.bar.Baz]",
        " [CIRCULAR REFERENCE: foo.bar.Qux]",
        "        [CIRCULAR REFERENCE: None.existing.class]",
        "        [CIRCULAR REFERENCE: foo.bar.Baz] ",
        "        [CIRCU:AA]",
        "        [CIRCULAR REFERENCE: foo.bar.Baz",
        "        [CIRCULAR REFERENCE: ]",
        "        [CIRCULAR REFERENCE: None existing class]");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Arrays.asList(
        "        [CIRCULAR REFERENCE: foo.bar.Baz]",
        " [CIRCULAR REFERENCE: foo.bar.Qux]",
        "        [CIRCULAR REFERENCE: None.existing.class]",
        "        [CIRCULAR REFERENCE: foo.bar.Baz] ",
        "        [CIRCU:AA]",
        "        [CIRCULAR REFERENCE: foo.bar.Baz",
        "        [CIRCULAR REFERENCE: ]",
        "        [CIRCULAR REFERENCE: None existing class]");
  }

  @Override
  public int expectedWarnings() {
    return 5;
  }
}
