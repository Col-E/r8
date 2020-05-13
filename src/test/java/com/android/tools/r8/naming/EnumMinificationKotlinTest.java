// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumMinificationKotlinTest extends KotlinTestBase {
  private static final String FOLDER = "minify_enum";
  private static final String MAIN_CLASS_NAME = "minify_enum.MainKt";
  private static final String ENUM_CLASS_NAME = "minify_enum.MinifyEnum";

  private final TestParameters parameters;
  private final boolean minify;

  @Parameterized.Parameters(name = "{0} target: {1} minify: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        KotlinTargetVersion.values(),
        BooleanUtils.values());
  }

  public EnumMinificationKotlinTest(
      TestParameters parameters, KotlinTargetVersion targetVersion, boolean minify) {
    super(targetVersion);
    this.parameters = parameters;
    this.minify = minify;
  }

  @Test
  public void b121221542() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(getKotlinJarFile(FOLDER))
            .addProgramFiles(getJavaJarFile(FOLDER))
            .addKeepMainRule(MAIN_CLASS_NAME)
            .addKeepClassRulesWithAllowObfuscation(ENUM_CLASS_NAME)
            .allowDiagnosticWarningMessages()
            .minification(minify)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .run(parameters.getRuntime(), MAIN_CLASS_NAME)
            .inspector();
    ClassSubject enumClass = inspector.clazz(ENUM_CLASS_NAME);
    assertThat(enumClass, isPresent());
    assertEquals(minify, enumClass.isRenamed());
    MethodSubject clinit = enumClass.clinit();
    assertThat(clinit, isPresent());
    assertEquals(
        0, Streams.stream(clinit.iterateInstructions(InstructionSubject::isThrow)).count());
  }
}
