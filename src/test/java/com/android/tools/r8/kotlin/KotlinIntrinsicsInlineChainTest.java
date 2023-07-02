// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.apimodel.ApiModelingTestHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinIntrinsicsInlineChainTest extends KotlinTestBase {

  private static final String FOLDER = "intrinsics";
  private static final String MAIN = FOLDER + ".InlineChainParameterCheckKt";

  @Parameterized.Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  private final TestParameters parameters;
  private final boolean allowAccessModification;

  public KotlinIntrinsicsInlineChainTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(kotlinParameters);
    this.parameters = parameters;
    this.allowAccessModification = allowAccessModification;
  }

  private static final KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(getKotlinFilesInResource(FOLDER), FOLDER)
          .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect());

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(
            compiledJars.getForConfiguration(kotlinc, targetVersion),
            kotlinc.getKotlinAnnotationJar())
        .addKeepMainRule(MAIN)
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .addDontObfuscate()
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .run(parameters.getRuntime(), MAIN, "foobar")
        .assertSuccessWithOutputLines("foobar", "field is foobar")
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(MAIN);
              assertThat(mainClass, isPresent());

              // Check that we have inlined all methods into main method.
              assertEquals(1, mainClass.allMethods().size());

              // Count the number of check parameter is not null.
              MethodSubject main = mainClass.mainMethod();
              long checkParameterIsNotNull = countCall(main, "checkParameterIsNotNull");
              long checkNotNullParameter = countCall(main, "checkNotNullParameter");
              if (kotlinc.getCompilerVersion().isGreaterThan(KotlinCompilerVersion.KOTLINC_1_6_0)) {
                assertEquals(0, checkNotNullParameter);
                assertEquals(0, checkParameterIsNotNull);
              } else if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.I)) {
                assertEquals(0, checkNotNullParameter);
                assertEquals(0, checkParameterIsNotNull);
              } else if (kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72)) {
                assertEquals(0, checkNotNullParameter);
                assertEquals(1, checkParameterIsNotNull);
              } else {
                assertEquals(1, checkNotNullParameter);
                assertEquals(0, checkParameterIsNotNull);
              }
            });
  }
}
