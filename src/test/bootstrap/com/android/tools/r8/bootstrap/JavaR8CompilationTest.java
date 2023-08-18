// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bootstrap;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaR8CompilationTest extends TestBase {

  private final TestParameters parameters;
  private final Path r8WithRelocatedDeps;

  public JavaR8CompilationTest(TestParameters parameters, Path r8WithRelocatedDeps) {
    this.parameters = parameters;
    this.r8WithRelocatedDeps = r8WithRelocatedDeps;
  }

  @Parameters(name = "{0}, java: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            // Use of APIs, such as java.nio.file.* are only available from 26+.
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.O)
            .withDexRuntimes()
            .build(),
        ImmutableList.of(ToolHelper.R8_WITH_RELOCATED_DEPS_17_JAR));
  }

  private static final Path MAIN_KEEP = Paths.get("src/main/keep.txt");

  private static void assertNoNests(CodeInspector inspector) {
    assertTrue(
        inspector.allClasses().stream().noneMatch(subj -> subj.getDexProgramClass().isInANest()));
  }

  @Test
  public void testR8CompiledWithR8() throws Exception {
    Assume.assumeTrue(JavaBootstrapUtils.exists(r8WithRelocatedDeps));
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramFiles(r8WithRelocatedDeps)
        .addKeepRuleFiles(MAIN_KEEP)
        .compile()
        .inspect(this::assertNotEmpty)
        .inspect(JavaR8CompilationTest::assertNoNests);
  }

  private void assertNotEmpty(CodeInspector inspector) {
    assertTrue(inspector.allClasses().size() > 0);
  }
}
