// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.ToolHelper.getKotlinCompilers;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collection;
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

  @Parameterized.Parameters(name = "{0}, target: {1}, kotlinc: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        KotlinTargetVersion.values(),
        getKotlinCompilers());
  }

  public MetadataRewriteInSealedClassNestedTest(
      TestParameters parameters, KotlinTargetVersion targetVersion, KotlinCompiler kotlinc) {
    super(targetVersion, kotlinc);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer sealedLibJarMap =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "nested"));

  @Test
  public void smokeTest() throws Exception {
    Path libJar = sealedLibJarMap.getForConfiguration(kotlinc, targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            ToolHelper.getKotlinStdlibJar(kotlinc), ToolHelper.getKotlinReflectJar(kotlinc), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInSealedClass_nested() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
            .addProgramFiles(sealedLibJarMap.getForConfiguration(kotlinc, targetVersion))
            .addKeepAllClassesRule()
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepAttributes(
                ProguardKeepAttributes.INNER_CLASSES, ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(
            ToolHelper.getKotlinStdlibJar(kotlinc), ToolHelper.getKotlinReflectJar(kotlinc), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }
}
