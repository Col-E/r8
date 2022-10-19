// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_4_20;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_7_0;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collection;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteDelegatedPropertyTest extends KotlinMetadataTestBase {

  private static final String PKG_LIB = PKG + ".delegated_property_lib";
  private static final String PKG_APP = PKG + ".delegated_property_app";
  private static final String EXPECTED =
      StringUtils.lines(
          "foobar",
          "var com.android.tools.r8.kotlin.metadata.delegated_property_lib.MyDelegatedProperty.oldName:"
              + " kotlin.String");

  private static final KotlinCompilerVersion MIN_SUPPORTED_KOTLIN_VERSION = KOTLINC_1_4_20;

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

  public MetadataRewriteDelegatedPropertyTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private final TestParameters parameters;

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
    testForJvm()
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path outputJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(
                kotlinc.getKotlinStdlibJar(),
                kotlinc.getKotlinReflectJar(),
                kotlinc.getKotlinAnnotationJar())
            .addKeepClassAndMembersRules(PKG_LIB + ".MyDelegatedProperty")
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectMetadata)
            .writeToZip();
    Path main =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(outputJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), outputJar)
        .addClasspath(main)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testInsufficientMetadataForLib() throws Exception {
    Path outputJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(
                kotlinc.getKotlinStdlibJar(),
                kotlinc.getKotlinReflectJar(),
                kotlinc.getKotlinAnnotationJar())
            .addKeepClassAndMembersRules(PKG_LIB + ".MyDelegatedProperty")
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .compile()
            .writeToZip();
    ProcessResult compileResult =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(outputJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compileRaw();
    Assert.assertEquals(1, compileResult.exitCode);
    if (kotlinParameters.isNewerThan(KOTLINC_1_7_0)) {
      assertThat(
          compileResult.stderr,
          containsString(
              "the feature \"references to synthetic java properties\" is only available since"
                  + " language version 1.9"));
    } else {
      assertThat(
          compileResult.stderr,
          containsString(
              "unsupported [reference to the synthetic extension property for a Java get/set"
                  + " method]"));
    }
  }

  private void inspectMetadata(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(PKG_LIB + ".MyDelegatedProperty");
    assertThat(clazz, isPresent());
    KotlinClassMetadata kotlinClassMetadata = clazz.getKotlinClassMetadata();
    Assert.assertNotNull(kotlinClassMetadata);
    String metadataAsString = KotlinMetadataWriter.kotlinMetadataToString("", kotlinClassMetadata);
    assertThat(metadataAsString, containsString("syntheticMethodForDelegate:"));
  }
}
