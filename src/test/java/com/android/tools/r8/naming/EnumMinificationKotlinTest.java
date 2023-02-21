// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
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

  @Parameterized.Parameters(name = "{0}, {1}, minify: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public EnumMinificationKotlinTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters, boolean minify) {
    super(kotlinParameters);
    this.parameters = parameters;
    this.minify = minify;
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(getKotlinFilesInResource(FOLDER), FOLDER)
          .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect());

  @Test
  public void b121221542() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramFiles(
                compiledJars.getForConfiguration(kotlinc, targetVersion),
                kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(getJavaJarFile(FOLDER))
            .addKeepMainRule(MAIN_CLASS_NAME)
            .addKeepClassRulesWithAllowObfuscation(ENUM_CLASS_NAME)
            .allowDiagnosticWarningMessages()
            .minification(minify)
            .setMinApi(parameters)
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .run(parameters.getRuntime(), MAIN_CLASS_NAME)
            .inspector();
    ClassSubject enumClass = inspector.clazz(ENUM_CLASS_NAME);
    assertThat(enumClass, isPresent());
    assertEquals(minify, enumClass.isRenamed());
    assertThat(enumClass.clinit(), isAbsent());
  }
}
