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
public class MetadataRewriteInSealedClassNestedTest extends KotlinMetadataTestBase {

  private static final String EXPECTED =
      StringUtils.lines(
          "DataTestControllerStartEvent(source=source, name=reason, display=false, stamp=0)",
          "[com.android.tools.r8.kotlin.metadata.sealed_lib.TestEvent.DiagnosticEvent,"
              + " com.android.tools.r8.kotlin.metadata.sealed_lib.Log]");
  private static final String PKG_LIB = PKG + ".sealed_lib";
  private static final String PKG_APP = PKG + ".sealed_app";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInSealedClassNestedTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> sealedLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path sealedLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(
                  getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "nested"))
              .compile();
      sealedLibJarMap.put(targetVersion, sealedLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = sealedLibJarMap.get(targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            ToolHelper.getKotlinStdlibJar(), ToolHelper.getKotlinReflectJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInSealedClass_nested() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(sealedLibJarMap.get(targetVersion))
            .addKeepAllClassesRule()
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepAttributes(
                ProguardKeepAttributes.INNER_CLASSES, ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            ToolHelper.getKotlinStdlibJar(), ToolHelper.getKotlinReflectJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }
}
