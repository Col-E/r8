// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import java.nio.file.Paths;
import org.junit.Test;

public class KotlinLoopTest extends KotlinDebugTestBase {

  DebugTestConfig config() {
    return new D8DebugTestConfig()
        .compileAndAdd(temp, Paths.get(ToolHelper.EXAMPLES_KOTLIN_BUILD_DIR, "loops.jar"));
  }

  @Test
  public void testStepOver() throws Throwable {
    runDebugTest(
        config(),
        "loops.LoopKt",
        breakpoint("loops.LoopKt", "main"),
        run(),
        checkLine("Loop.kt", 13),
        run());
  }
}
