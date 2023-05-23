// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.stacktraces;

import com.android.tools.r8.utils.StringUtils;
import java.util.Collections;
import java.util.List;

/** This is a reproduction of b/283837159 */
public class ResidualSignatureOnOuterFrameStackTrace implements StackTraceForTest {

  @Override
  public List<String> obfuscatedStackTrace() {
    return Collections.singletonList("\tat mapping.g(SourceFile)");
  }

  @Override
  public String mapping() {
    return StringUtils.joinLines(
        "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
        "kotlinx.coroutines.BuildersKt -> mapping:",
        "  1:1:void pruned.class.method(kotlinx.coroutines.CoroutineScope):10:10 -> g",
        "  2:2:void pruned.class.method(kotlinx.coroutines.CoroutineScope):0:0 -> g",
        "  2:2:void pruned.class.method(kotlinx.coroutines.CoroutineScope):0 -> g",
        // The residual signature should be placed on the first mapped range.
        "  # {'id':'com.android.tools.r8.residualsignature', 'signature':'(LX;)V'}",
        "  3:3:void pruned.class.method(kotlinx.coroutines.CoroutineScope):30:30 -> g");
  }

  @Override
  public List<String> retracedStackTrace() {
    return Collections.singletonList("\tat pruned.class.method(class.java)");
  }

  @Override
  public List<String> retraceVerboseStackTrace() {
    return Collections.singletonList(
        "\tat pruned.class.void method(kotlinx.coroutines.CoroutineScope)(class.java)");
  }

  @Override
  public int expectedWarnings() {
    return 0;
  }
}
