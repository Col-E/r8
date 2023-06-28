// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing.kotlin;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.KotlinTestBase.getCompileMemoizer;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestBase.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
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
  private final KotlinTestParameters kotlinParameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  private static final String PKG = SimpleKotlinEnumUnboxingTest.class.getPackage().getName();
  private static final KotlinCompileMemoizer jars =
      getCompileMemoizer(
          Paths.get(
              ToolHelper.TESTS_DIR,
              "java",
              DescriptorUtils.getBinaryNameFromJavaType(PKG),
              "Main.kt"));

  @Parameters(name = "{0}, {1}, valueOpt: {2}, keep: {3}")
  public static List<Object[]> enumUnboxingTestParameters() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values(),
        getAllEnumKeepRules());
  }

  public SimpleKotlinEnumUnboxingTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean enumValueOptimization,
      EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.kotlinParameters = kotlinParameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addProgramFiles(
            jars.getForConfiguration(kotlinParameters),
            kotlinParameters.getCompiler().getKotlinStdlibJar(),
            kotlinParameters.getCompiler().getKotlinAnnotationJar())
        .addKeepMainRule(PKG + ".MainKt")
        .addKeepRules(enumKeepRules.getKeepRules())
        .addKeepRuntimeVisibleAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(PKG + ".Color"))
        .allowDiagnosticMessages()
        .setMinApi(parameters)
        .compile()
        .inspectDiagnosticMessages(
            messages ->
                messages
                    .assertNoErrors()
                    .assertAllWarningsMatch(
                        diagnosticMessage(
                            containsString("Resource 'META-INF/MANIFEST.MF' already exists."))))
        .run(parameters.getRuntime(), PKG + ".MainKt")
        .assertSuccessWithOutputLines("RED", "GREEN", "BLUE");
  }
}
