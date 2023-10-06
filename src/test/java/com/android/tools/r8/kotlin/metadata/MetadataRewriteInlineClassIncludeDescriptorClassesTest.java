// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.Matchers;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInlineClassIncludeDescriptorClassesTest extends KotlinMetadataTestBase {

  private final String EXPECTED = StringUtils.lines("Hello World!");
  private static final KotlinCompilerVersion MIN_SUPPORTED_KOTLIN_VERSION = KOTLINC_1_6_0;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withOldCompilersStartingFrom(MIN_SUPPORTED_KOTLIN_VERSION)
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_KOTLIN_VERSION)
            .withAllTargetVersions()
            .build());
  }

  public MetadataRewriteInlineClassIncludeDescriptorClassesTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(
          getKotlinFileInTest(PKG_PREFIX + "/inline_class_fun_descriptor_classes_lib", "lib"),
          getKotlinFileInTest(
              PKG_PREFIX + "/inline_class_fun_descriptor_classes_lib", "keepForApi"));
  private final TestParameters parameters;

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJars.getForConfiguration(kotlinc, targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(
                    PKG_PREFIX + "/inline_class_fun_descriptor_classes_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".inline_class_fun_descriptor_classes_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            // kotlinc will generate a method for the unboxed type on the form login-XXXXX(String).
            // We define an annotation that specify we keep the method and descriptor classes.
            .addKeepRules(
                "-keepclasseswithmembers class * { @"
                    + PKG
                    + ".inline_class_fun_descriptor_classes_lib.KeepForApi *; }")
            .addKeepRules(
                "-keepclassmembers,includedescriptorclasses class * { @"
                    + PKG
                    + ".inline_class_fun_descriptor_classes_lib.KeepForApi *; }")
            .compile()
            .inspect(
                inspector -> {
                  // TODO(b/208209210): Perhaps this should be kept.
                  assertThat(
                      inspector.clazz(
                          PKG + ".inline_class_fun_descriptor_classes_lib.KeepForApi.Password"),
                      not(Matchers.isPresent()));
                })
            .writeToZip();
    ProcessResult kotlinCompileAppResult =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(
                    PKG_PREFIX + "/inline_class_fun_descriptor_classes_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compileRaw();
    assertEquals(1, kotlinCompileAppResult.exitCode);
    assertThat(
        kotlinCompileAppResult.stderr,
        containsString(unresolvedReferenceMessage(kotlinParameters, "Password")));
  }
}
