// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SynchronizedCollectionTest extends DesugaredLibraryTestBase {

  private static final Path INPUT_JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA9_BUILD_DIR + "desugaredlib.jar");
  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("[1]", "2", "[2, 3]", "true", "2", "2", "2");
  private static final String MAIN_CLASS = "desugaredlib.SynchronizedCollectionMain";

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withAllApiLevels()
            .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
            .build(),
        BooleanUtils.values());
  }

  public SynchronizedCollectionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testExecutionD8() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addProgramFiles(INPUT_JAR)
          .run(parameters.getRuntime(), MAIN_CLASS)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      return;
    }
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addProgramFiles(INPUT_JAR)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testExecutionR8() throws Exception {
    // Desugared library tests do not make sense in the Cf to Cf, and the JVM is already tested
    // in the D8 test. Just return.
    Assume.assumeFalse(parameters.isCfRuntime());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addProgramFiles(INPUT_JAR)
        .addKeepMainRule(MAIN_CLASS)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}
