// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** This is a regression test for b/259389417. */
@RunWith(Parameterized.class)
public class MetadataRewriteEnumTest extends KotlinMetadataTestBase {

  private final String[] EXPECTED =
      new String[] {"UP", "RIGHT", "DOWN", "LEFT", "UP", "RIGHT", "DOWN", "LEFT"};
  private final String DIRECTION_TYPE_NAME = PKG + ".enum_lib.Direction";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataRewriteEnumTest(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer jarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/enum_lib", "lib"));

  @Test
  public void smokeTest() throws Exception {
    Path libJar = jarMap.getForConfiguration(kotlinc, targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/enum_app", "main"))
            .compile();
    testForRuntime(parameters)
        .addProgramFiles(
            libJar, output, kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar())
        .run(parameters.getRuntime(), PKG + ".enum_app.MainKt")
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult r8libResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(jarMap.getForConfiguration(kotlinc, targetVersion))
            .addClasspathFiles(kotlinc.getKotlinStdlibJar())
            .addKeepKotlinMetadata()
            .addKeepEnumsRule()
            .addKeepClassRules(DIRECTION_TYPE_NAME)
            .addKeepClassAndMembersRulesWithAllowObfuscation(DIRECTION_TYPE_NAME)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject direction = inspector.clazz(DIRECTION_TYPE_NAME);
                  assertThat(direction, isPresentAndNotRenamed());
                  direction.allFields().forEach(field -> assertTrue(field.isRenamed()));
                });
    Path libJar = r8libResult.writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/enum_app", "main"))
            .compile();
    Path path =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar)
            .addProgramFiles(output)
            .addKeepAllClassesRule()
            .addApplyMapping(r8libResult.getProguardMap())
            .compile()
            .writeToZip();
    // TODO(b/259389417): We should rename enum values in metadata.
    testForRuntime(parameters)
        .addProgramFiles(libJar, kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), path)
        .run(parameters.getRuntime(), PKG + ".enum_app.MainKt")
        .assertFailureWithErrorThatThrows(NoSuchFieldError.class);
  }
}
