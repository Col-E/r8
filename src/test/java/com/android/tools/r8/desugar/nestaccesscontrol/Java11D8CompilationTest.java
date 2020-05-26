// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Java11D8CompilationTest extends TestBase {

  public Java11D8CompilationTest(TestParameters parameters) {}

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static void assertNoNests(CodeInspector inspector) {
    assertTrue(
        inspector.allClasses().stream().noneMatch(subj -> subj.getDexProgramClass().isInANest()));
  }

  @Test
  public void testR8CompiledWithD8() throws Exception {
    testForD8()
        .addProgramFiles(ToolHelper.R8_WITH_RELOCATED_DEPS_11_JAR)
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .compile()
        .inspect(Java11D8CompilationTest::assertNoNests);
  }
}
