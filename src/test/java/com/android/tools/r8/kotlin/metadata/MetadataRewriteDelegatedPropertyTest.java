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
import com.android.tools.r8.utils.codeinspector.CodeInspector;
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

  private static final String PKG_APP = PKG + ".delegated_property_app";
  private static final String EXPECTED_MAIN =
      StringUtils.lines(
          "Initial string has been read in CustomDelegate from 'x'",
          "Initial string has been read in CustomDelegate from 'x'",
          "New value has been read in CustomDelegate from 'x'",
          "New value has been read in CustomDelegate from 'x'",
          "null",
          "New value has been read in CustomDelegate from 'x'");

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
  private static Map<KotlinTargetVersion, Path> jars = new HashMap<>();

  @BeforeClass
  public static void createJar() throws Exception {
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path baseLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(
                  getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
              .compile();
      jars.put(targetVersion, baseLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), ToolHelper.getKotlinReflectJar())
        .addClasspath(jars.get(targetVersion))
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED_MAIN);
  }


  @Test
  public void testMetadataForLib() throws Exception {
    Path outputJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(jars.get(targetVersion))
            .addKeepAllClassesRule()
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(
                inspector ->
                    assertEqualMetadata(new CodeInspector(jars.get(targetVersion)), inspector))
            .writeToZip();
    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), ToolHelper.getKotlinReflectJar())
        .addClasspath(outputJar)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED_MAIN);
  }
}
