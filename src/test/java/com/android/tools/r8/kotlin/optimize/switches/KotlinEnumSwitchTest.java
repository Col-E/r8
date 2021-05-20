// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.optimize.switches;

import static com.android.tools.r8.ToolHelper.getMostRecentKotlinAnnotationJar;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.KotlinCompilerTool;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinEnumSwitchTest extends KotlinTestBase {

  private final boolean enableSwitchMapRemoval;
  private final TestParameters parameters;

  @Parameters(name = "{1}, enable switch map removal: {0}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  private static final KotlinCompileMemoizer kotlinJars =
      getCompileMemoizer(getKotlinFilesInResource("enumswitch"))
          .configure(KotlinCompilerTool::includeRuntime);

  public KotlinEnumSwitchTest(
      boolean enableSwitchMapRemoval,
      TestParameters parameters,
      KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.enableSwitchMapRemoval = enableSwitchMapRemoval;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(
            kotlinJars.getForConfiguration(kotlinParameters), getMostRecentKotlinAnnotationJar())
        .addKeepMainRule("enumswitch.EnumSwitchKt")
        .addOptionsModification(
            options -> {
              options.enableEnumValueOptimization = enableSwitchMapRemoval;
              options.enableEnumSwitchMapRemoval = enableSwitchMapRemoval;
            })
        .applyIf(
            kotlinParameters.getCompiler().is(KotlinCompilerVersion.KOTLINC_1_5_0_M2),
            R8TestBuilder::allowDiagnosticMessages,
            R8TestBuilder::allowDiagnosticWarningMessages)
        .setMinApi(parameters.getApiLevel())
        .noMinification()
        .compile()
        .assertNoErrorMessages()
        .applyIf(
            kotlinParameters.getCompiler().is(KotlinCompilerVersion.KOTLINC_1_5_0_M2),
            TestBase::verifyAllInfoFromGenericSignatureTypeParameterValidation,
            TestCompileResult::assertNoInfoMessages)
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz("enumswitch.EnumSwitchKt");
              assertThat(classSubject, isPresent());

              ClassSubject mapClassSubject =
                  inspector.clazz("enumswitch.EnumSwitchKt$WhenMappings");
              assertNotEquals(enableSwitchMapRemoval, mapClassSubject.isPresent());
            })
        .run(parameters.getRuntime(), "enumswitch.EnumSwitchKt")
        .assertSuccessWithOutputLines("N", "S", "E", "W", "N", "S", "E", "W");
  }
}
