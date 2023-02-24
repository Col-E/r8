// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Kotlin has limited support for metadata of older versions. In particular, kotlinc 1.5 has
 * deprecated byte-code version which is expected by kotlinc 1.3. The expectation is that if we
 * compile with kotlinc 1.3 and then compile with R8 with a new version of the kolin-metadata-jvm
 * library, the kotlin library is no longer usable in kotlinc 1.3. However, it should be usable in
 * kotlinc 1.5.
 */
@RunWith(Parameterized.class)
public class MetadataFirstToLatestTest extends KotlinMetadataTestBase {

  private final String EXPECTED = StringUtils.lines("foo");
  private static final String PKG_LIB = PKG + ".crossinline_anon_lib";
  private static final String PKG_APP = PKG + ".crossinline_anon_app";
  private final TestParameters parameters;

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"));

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withOldCompilersIfSet()
            .withTargetVersion(KotlinTargetVersion.JAVA_8)
            .build());
  }

  public MetadataFirstToLatestTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void smokeTest() throws Exception {
    runTest(
        KotlinCompilerVersion.MAX_SUPPORTED_VERSION,
        libJars.getForConfiguration(kotlinc, targetVersion),
        kotlinc.getKotlinStdlibJar());
  }

  @Test
  public void testOnFirst() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addKeepAllClassesRule()
            .addKeepAllAttributes()
            .addOptionsModification(
                options -> {
                  // Ensure that we rewrite the metadata with kotlin-metadata-jvm library.
                  options.testing.keepMetadataInR8IfNotRewritten = false;
                })
            .compile()
            .writeToZip();
    Path stdLibJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addKeepAllClassesRule()
            .addKeepAllAttributes()
            .allowDiagnosticWarningMessages()
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .writeToZip();
    if (kotlinParameters.isOlderThan(KotlinCompilerVersion.KOTLINC_1_4_20)) {
      AssertionError assertionError =
          assertThrows(
              AssertionError.class,
              () -> {
                runTest(kotlinParameters.getCompiler().getCompilerVersion(), libJar, stdLibJar);
              });
      assertThat(
          assertionError.getMessage(),
          containsString("compiled with an incompatible version of Kotlin"));
    } else {
      runTest(kotlinParameters.getCompiler().getCompilerVersion(), libJar, stdLibJar);
    }
  }

  @Test
  public void testOnLatest() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addKeepAllClassesRule()
            .addKeepAllAttributes()
            .compile()
            .writeToZip();
    Path stdLibJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addKeepAllClassesRule()
            .addKeepAllAttributes()
            .allowDiagnosticWarningMessages()
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .writeToZip();
    runTest(KotlinCompilerVersion.MAX_SUPPORTED_VERSION, libJar, stdLibJar);
  }

  private void runTest(KotlinCompilerVersion kotlinCompilerVersion, Path libJar, Path stdLibJar)
      throws Exception {
    Path output =
        kotlinc(
                parameters.getRuntime().asCf(),
                new KotlinCompiler(kotlinCompilerVersion),
                targetVersion)
            .addClasspathFiles(libJar, stdLibJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .noStdLib()
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(stdLibJar, libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }
}
