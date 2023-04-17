// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing.enummerging;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticEnumMergingTest extends EnumUnboxingTestBase {

  private static final Path JDK17_JAR =
      Paths.get(ToolHelper.TESTS_BUILD_DIR, "examplesJava17").resolve("enumStatic" + JAR_EXTENSION);
  private static final String EXPECTED_RESULT = StringUtils.lines("-17");
  private static final String MAIN = "enumStatic.EnumStaticMain";

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public StaticEnumMergingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(JDK17_JAR)
        .addKeepMainRule(MAIN)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> opt.testing.enableEnumWithSubtypesUnboxing = true)
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addOptionsModification(opt -> opt.testing.enableEnumUnboxingDebugLogs = true)
        .setMinApi(parameters)
        .allowDiagnosticInfoMessages()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }
}
