// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinIntrinsicsInlineTest extends KotlinTestBase {
  private static final String FOLDER = "intrinsics";
  private static final String MAIN = FOLDER + ".InlineKt";

  @Parameterized.Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  private final TestParameters parameters;
  private final boolean allowAccessModification;

  public KotlinIntrinsicsInlineTest(
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
  public void b139432507() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(
            compiledJars.getForConfiguration(kotlinc, targetVersion),
            kotlinc.getKotlinAnnotationJar())
        .addKeepRules(
            StringUtils.lines(
                "-keepclasseswithmembers class " + MAIN + "{", "  public static *** *(...);", "}"))
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticWarningMessages()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(
            inspector -> {
              ClassSubject main = inspector.clazz(MAIN);
              assertThat(main, isPresent());

              // Note that isSupported itself has a parameter whose null check would be inlined
              // with -allowaccessmodification.
              MethodSubject isSupported = main.uniqueMethodWithOriginalName("isSupported");
              assertThat(isSupported, isPresent());
              assertEquals(
                  kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72) ? 1 : 0,
                  countCall(isSupported, "checkParameterIsNotNull"));

              // In general cases, null check won't be invoked only once or twice, hence no subtle
              // situation in double inlining.
              MethodSubject containsArray = main.uniqueMethodWithOriginalName("containsArray");
              assertThat(containsArray, isPresent());
              assertEquals(0, countCall(containsArray, "checkParameterIsNotNull"));
            });
  }

  @Test
  public void b139432507_isSupported() throws Exception {
    assumeTrue("Different inlining behavior on CF backend", parameters.isDexRuntime());
    testSingle(
        "isSupported",
        kotlinc.is(KotlinCompilerVersion.KOTLINC_1_3_72)
            && parameters.getApiLevel().isLessThan(AndroidApiLevel.I));
  }

  @Test
  public void b139432507_containsArray() throws Exception {
    assumeTrue("Different inlining behavior on CF backend", parameters.isDexRuntime());
    // One for each of the method's own arguments, unless building with
    // -allowaccessmodification.
    testSingle("containsArray", allowAccessModification);
  }

  private void testSingle(String methodName, boolean checkParameterIsNotNullCountIsArity)
      throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(
            compiledJars.getForConfiguration(kotlinc, targetVersion),
            kotlinc.getKotlinAnnotationJar())
        .addKeepRules(
            StringUtils.lines(
                "-keepclasseswithmembers class " + MAIN + "{",
                "  public static *** " + methodName + "(...);",
                "}"))
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticWarningMessages()
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(
            inspector -> {
              ClassSubject main = inspector.clazz(MAIN);
              assertThat(main, isPresent());

              MethodSubject method = main.uniqueMethodWithOriginalName(methodName);
              assertThat(method, isPresent());
              int arity = method.getMethod().getReference().getArity();
              assertEquals(
                  checkParameterIsNotNullCountIsArity ? arity : 0,
                  countCall(method, "checkParameterIsNotNull"));
            });
  }
}
