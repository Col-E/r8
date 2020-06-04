// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteDelegatedPropertyTest extends KotlinMetadataTestBase {

  private static final String PKG_LIB = PKG + ".delegated_property_lib";
  private static final String PKG_APP = PKG + ".delegated_property_app";
  private static final String EXPECTED_MAIN =
      StringUtils.lines(
          "foo has been assigned to 'customDelegate' in"
              + " com.android.tools.r8.kotlin.metadata.delegated_property_lib.Delegates",
          "foo has been read in CustomDelegate from 'customDelegate' in"
              + " com.android.tools.r8.kotlin.metadata.delegated_property_lib.Delegates",
          "foo",
          "read-only has been read in CustomReadOnlyDelegate from 'customReadOnlyDelegate' in"
              + " com.android.tools.r8.kotlin.metadata.delegated_property_lib.Delegates",
          "read-only",
          "Generating lazy string",
          "42",
          "Hello World!",
          "Hello World!",
          "Jane Doe",
          "42",
          "Checking property for image",
          "Checking property for text",
          "image_id",
          "text_id");
  private static final String EXPECTED_REFLECT =
      StringUtils.lines(
          "foo has been assigned to 'customDelegate' in"
              + " com.android.tools.r8.kotlin.metadata.delegated_property_lib.Delegates",
          "foo");

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteDelegatedPropertyTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private final TestParameters parameters;
  private static Map<KotlinTargetVersion, Path> libJars = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path baseLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(
                  getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"))
              .compile();
      libJars.put(targetVersion, baseLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJars.get(targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED_MAIN);
  }

  @Test
  public void smokeTestReflect() throws Exception {
    Path libJar = libJars.get(targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(
                    DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main_reflect"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            ToolHelper.getKotlinStdlibJar(), ToolHelper.getKotlinReflectJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".Main_reflectKt")
        .assertSuccessWithOutput(EXPECTED_REFLECT);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(libJars.get(targetVersion))
            .addKeepRules("-keep class " + PKG_LIB + ".Delegates { *; }")
            .addKeepRules("-keep class " + PKG_LIB + ".Resource { *; }")
            .addKeepRules("-keep class " + PKG_LIB + ".User { *; }")
            .addKeepRules("-keep class " + PKG_LIB + ".ProvidedDelegates { *; }")
            .compile()
            // TODO(b/157988734): When we start modeling localDelegatedProperties, inspect the code.
            .writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED_MAIN);
  }

  @Test
  public void testMetadataForReflect() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(libJars.get(targetVersion))
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepRules("-keep class " + PKG_LIB + ".Delegates { *; }")
            .addKeepRules("-keep class " + PKG_LIB + ".Resource { *; }")
            .addKeepRules("-keep class " + PKG_LIB + ".CustomDelegate { *; }")
            .compile()
            .writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(
                    DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main_reflect"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            ToolHelper.getKotlinStdlibJar(), ToolHelper.getKotlinReflectJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".Main_reflectKt")
        .assertSuccessWithOutput(EXPECTED_REFLECT);
  }
}
