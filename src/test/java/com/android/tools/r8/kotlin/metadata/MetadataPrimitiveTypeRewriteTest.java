// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_4_20;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.JvmTestRunResult;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataPrimitiveTypeRewriteTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;
  private final String EXPECTED = "Hello World";
  private static final String PKG_LIB = PKG + ".primitive_type_rewrite_lib";
  private static final String PKG_APP = PKG + ".primitive_type_rewrite_app";

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_VERSION)
            .withAllTargetVersions()
            .build());
  }

  public MetadataPrimitiveTypeRewriteTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"));

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJars.getForConfiguration(kotlinc, targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testRenamingRenameUnit() throws Exception {
    runTest(false);
  }

  @Test
  public void testRenamingKeepUnit() throws Exception {
    runTest(true);
  }

  private void runTest(boolean keepUnit) throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(
                kotlinc.getKotlinStdlibJar(),
                kotlinc.getKotlinAnnotationJar(),
                libJars.getForConfiguration(kotlinc, targetVersion))
            .addKeepAllClassesRuleWithAllowObfuscation()
            .addKeepRules("-keep class " + PKG_LIB + ".LibKt { *; }")
            .addKeepRules("-keep class kotlin.Metadata { *; }")
            .applyIf(keepUnit, b -> b.addKeepRules("-keep class kotlin.Unit { *; }"))
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .allowDiagnosticWarningMessages()
            .compile()
            .inspect(
                codeInspector -> {
                  final ClassSubject clazz = codeInspector.clazz("kotlin.Unit");
                  assertThat(clazz, isPresentAndRenamed(!keepUnit));
                })
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .writeToZip();
    boolean expectingCompilationError = kotlinParameters.isOlderThan(KOTLINC_1_4_20) && !keepUnit;
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile(expectingCompilationError);
    if (expectingCompilationError) {
      return;
    }
    final JvmTestRunResult runResult =
        testForJvm(parameters)
            .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
            .addClasspath(output)
            .run(parameters.getRuntime(), PKG_APP + ".MainKt");
    if (keepUnit) {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    } else {
      runResult.assertFailureWithErrorThatMatches(
          allOf(
              containsString("java.lang.NoSuchMethodError:"),
              containsString(
                  "com.android.tools.r8.kotlin.metadata.primitive_type_rewrite_lib.LibKt.foo()")));
    }
  }
}
