// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.cf.methodhandles.fields.ClassFieldMethodHandleTest.Main.assertEquals;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
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
  public void testKotlincFailsRenamed() throws Exception {
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
    ProcessResult kotlinCompileResult =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(r8libResult.writeToZip())
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/enum_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compileRaw();
    assertEquals(1, kotlinCompileResult.exitCode);
    assertThat(kotlinCompileResult.stderr, containsString("unresolved reference"));
    assertThat(kotlinCompileResult.stderr, containsString("Direction.UP"));
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
                  KmClassSubject kmClass = direction.getKmClass();
                  List<String> expectedEnumNames = Arrays.asList("a", "b", "c", "d");
                  Assert.assertEquals(expectedEnumNames, kmClass.getEnumEntries());
                });
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(jarMap.getForConfiguration(kotlinc, targetVersion))
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/enum_app", "main"))
            .compile();
    Path path =
        testForR8(parameters.getBackend())
            .addClasspathFiles(
                kotlinc.getKotlinStdlibJar(),
                kotlinc.getKotlinReflectJar(),
                jarMap.getForConfiguration(kotlinc, targetVersion))
            .addProgramFiles(output)
            .addKeepAllClassesRule()
            .addApplyMapping(r8libResult.getProguardMap())
            .compile()
            .writeToZip();
    testForRuntime(parameters)
        .addProgramFiles(
            r8libResult.writeToZip(),
            kotlinc.getKotlinStdlibJar(),
            kotlinc.getKotlinReflectJar(),
            path)
        .run(parameters.getRuntime(), PKG + ".enum_app.MainKt")
        .assertSuccessWithOutputLines(EXPECTED);
  }
}
