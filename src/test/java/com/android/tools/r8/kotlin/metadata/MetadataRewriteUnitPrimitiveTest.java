// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static com.android.tools.r8.KotlinCompilerTool.KotlinTargetVersion.JAVA_8;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteUnitPrimitiveTest extends KotlinMetadataTestBase {

  private static final String PKG_LIB = PKG + ".unit_primitive_lib";
  private static final String PKG_APP = PKG + ".unit_primitive_app";
  private final TestParameters parameters;

  private static final String EXPECTED =
      StringUtils.lines(
          "fun " + PKG_LIB + ".Lib.testInt(): kotlin.Int",
          "42",
          "fun " + PKG_LIB + ".Lib.testIntArray(): kotlin.IntArray",
          "42",
          "fun " + PKG_LIB + ".Lib.testUInt(): kotlin.UInt",
          "42",
          "fun " + PKG_LIB + ".Lib.testUIntArray(): kotlin.UIntArray",
          "UIntArray(storage=[42])",
          "fun " + PKG_LIB + ".Lib.testUnit(): kotlin.Unit",
          "testUnit",
          "kotlin.Unit");

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_VERSION)
            .withTargetVersion(JAVA_8)
            .build());
  }

  public MetadataRewriteUnitPrimitiveTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer kotlincLibJar =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"));

  @Test
  public void smokeTest() throws Exception {
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(kotlincLibJar.getForConfiguration(kotlinc, targetVersion))
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(),
            kotlinc.getKotlinReflectJar(),
            kotlincLibJar.getForConfiguration(kotlinc, targetVersion))
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(
                kotlincLibJar.getForConfiguration(kotlinc, targetVersion),
                kotlinc.getKotlinStdlibJar())
            .addKeepClassAndMembersRules(PKG_LIB + ".*")
            .addKeepAttributes(
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .addKeepKotlinMetadata()
            .allowDiagnosticWarningMessages()
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .inspect(
                inspector ->
                    assertEqualMetadata(
                        inspector,
                        new CodeInspector(
                            kotlincLibJar.getForConfiguration(kotlinc, targetVersion))))
            .writeToZip();
    Path main =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar)
        .addClasspath(main)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }
}
