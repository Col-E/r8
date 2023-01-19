// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromJavaType;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.kotlin.metadata.type_raw_lib.JavaLibraryClass;
import com.android.tools.r8.kotlin.metadata.type_raw_lib.JavaLibraryClass.GenericClass;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteRawTest extends KotlinMetadataTestBase {

  private final String EXPECTED = StringUtils.lines("Hello World");
  private static final String PKG_LIB = PKG + ".type_raw_lib";
  private static final String PKG_APP = PKG + ".type_raw_app";

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_VERSION)
            .withAllTargetVersions()
            .build());
  }

  public MetadataRewriteRawTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static Path javaLibZip;

  @BeforeClass
  public static void before() throws Exception {
    Path zipFile = getStaticTemp().newFolder().toPath().resolve("out.jar");
    javaLibZip =
        ZipBuilder.builder(zipFile)
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(JavaLibraryClass.class),
                ToolHelper.getClassFileForTestClass(GenericClass.class))
            .build();
  }

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(getKotlinFileInTest(getBinaryNameFromJavaType(PKG_LIB), "lib"))
          .configure(kotlinCompilerTool -> kotlinCompilerTool.addClasspathFiles(javaLibZip));
  private final TestParameters parameters;

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJars.getForConfiguration(kotlinc, targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar, javaLibZip)
            .addSourceFiles(getKotlinFileInTest(getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar, javaLibZip)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(
                kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar(), javaLibZip)
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .addKeepRules("-keep class " + PKG_LIB + ".ClassWithRawType { *; }")
            .addKeepAttributes(
                ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS,
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(
                inspector ->
                    assertEqualMetadataWithStringPoolValidation(
                        new CodeInspector(libJars.getForConfiguration(kotlinc, targetVersion)),
                        inspector,
                        (addedStrings, addedNonInitStrings) -> {}))
            .writeToZip();
    Path main =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar, javaLibZip)
            .addSourceFiles(getKotlinFileInTest(getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar, javaLibZip)
        .addClasspath(main)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }
}
