// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

public class Java11D8CompilationTest extends TestBase {

  private static void assertNoNests(CodeInspector inspector) {
    assertTrue(inspector.allClasses().stream().noneMatch(subj -> subj.getDexClass().isInANest()));
  }

  @Test
  public void testR8CompiledWithD8() throws Exception {
    testForD8()
        .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_JAR_11)
        .compile()
        .inspect(Java11D8CompilationTest::assertNoNests);
  }
}
