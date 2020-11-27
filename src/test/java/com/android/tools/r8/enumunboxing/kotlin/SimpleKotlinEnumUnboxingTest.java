// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing.kotlin;

import static com.android.tools.r8.KotlinTestBase.getCompileMemoizer;
import static com.android.tools.r8.ToolHelper.getKotlinCompilers;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinTestBase.KotlinCompileMemoizer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Paths;
import java.util.List;
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
  private final KotlinCompiler kotlinCompiler;

  private static final String PKG = SimpleKotlinEnumUnboxingTest.class.getPackage().getName();
  private static final KotlinCompileMemoizer jars =
      getCompileMemoizer(
          Paths.get(
              ToolHelper.TESTS_DIR,
              "java",
              DescriptorUtils.getBinaryNameFromJavaType(PKG),
              "Main.kt"));

  @Parameters(name = "{0}, valueOpt: {1}, keep: {2}, kotlin targetVersion: {3}, kotlinc: {4}")
  public static List<Object[]> enumUnboxingTestParameters() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        getAllEnumKeepRules(),
        KotlinTargetVersion.values(),
        getKotlinCompilers());
  }

  public SimpleKotlinEnumUnboxingTest(
      TestParameters parameters,
      boolean enumValueOptimization,
      EnumKeepRules enumKeepRules,
      KotlinTargetVersion targetVersion,
      KotlinCompiler kotlinCompiler) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
    this.targetVersion = targetVersion;
    this.kotlinCompiler = kotlinCompiler;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addProgramFiles(jars.getForConfiguration(kotlinCompiler, targetVersion))
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
