// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimpleKotlinEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;
  private final KotlinTargetVersion targetVersion;

  private static final String PKG = SimpleKotlinEnumUnboxingTest.class.getPackage().getName();
  private static Map<KotlinTargetVersion, Path> jars = new HashMap<>();

  @Parameters(name = "{0}, valueOpt: {1}, keep: {2}, kotlin targetVersion: {3}")
  public static List<Object[]> enumUnboxingTestParameters() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        getAllEnumKeepRules(),
        KotlinTargetVersion.values());
  }

  public SimpleKotlinEnumUnboxingTest(
      TestParameters parameters,
      boolean enumValueOptimization,
      EnumKeepRules enumKeepRules,
      KotlinTargetVersion targetVersion) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
    this.targetVersion = targetVersion;
  }

  @BeforeClass
  public static void createLibJar() throws Exception {
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      jars.put(
          targetVersion,
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(
                  Paths.get(
                      ToolHelper.TESTS_DIR,
                      "java",
                      DescriptorUtils.getBinaryNameFromJavaType(PKG),
                      "Main.kt"))
              .compile());
    }
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addProgramFiles(jars.get(targetVersion))
        .addKeepMainRule(PKG + ".MainKt")
        .addKeepRules(enumKeepRules.getKeepRules())
        .addKeepRuntimeVisibleAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .allowDiagnosticInfoMessages()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectDiagnosticMessages(
            m -> {
              assertEnumIsUnboxed(
                  PKG + ".Color", SimpleKotlinEnumUnboxingTest.class.getSimpleName(), m);
            })
        .run(parameters.getRuntime(), PKG + ".MainKt")
        .assertSuccessWithOutputLines("RED", "GREEN", "BLUE");
  }
}
