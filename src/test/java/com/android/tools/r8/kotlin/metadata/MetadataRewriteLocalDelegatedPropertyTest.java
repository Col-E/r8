// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

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
public class MetadataRewriteLocalDelegatedPropertyTest extends KotlinMetadataTestBase {

  private static final String PKG_APP = PKG + ".local_delegated_property_app";
  private static final String EXPECTED_MAIN =
      StringUtils.lines(
          "Initial string has been read in CustomDelegate from 'x'",
          "Initial string has been read in CustomDelegate from 'x'",
          "New value has been read in CustomDelegate from 'x'",
          "New value has been read in CustomDelegate from 'x'",
          "null",
          "New value has been read in CustomDelegate from 'x'");

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataRewriteLocalDelegatedPropertyTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private final TestParameters parameters;
  private static final KotlinCompileMemoizer jars =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"));

  @Test
  public void smokeTest() throws Exception {
    testForJvm()
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar())
        .addClasspath(jars.getForConfiguration(kotlinc, targetVersion))
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED_MAIN);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path outputJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(
                kotlinc.getKotlinStdlibJar(),
                kotlinc.getKotlinReflectJar(),
                kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(jars.getForConfiguration(kotlinc, targetVersion))
            .addKeepAllClassesRule()
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(
                inspector ->
                    assertEqualMetadataWithStringPoolValidation(
                        new CodeInspector(jars.getForConfiguration(kotlinc, targetVersion)),
                        inspector,
                        (addedStrings, addedNonInitStrings) -> {}))
            .writeToZip();
    testForJvm()
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar())
        .addClasspath(outputJar)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED_MAIN);
  }
}
