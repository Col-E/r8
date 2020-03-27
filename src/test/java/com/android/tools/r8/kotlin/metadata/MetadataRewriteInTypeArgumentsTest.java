// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
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
public class MetadataRewriteInTypeArgumentsTest extends KotlinMetadataTestBase {
  private static final String EXPECTED =
      StringUtils.lines(
          "Hello World!",
          "42",
          "1",
          "42",
          "42",
          "1",
          "42",
          "42",
          "42",
          "1",
          "42",
          "1",
          "42",
          "42",
          "42",
          "42",
          "42",
          "1",
          "2",
          "7",
          "42");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInTypeArgumentsTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> jarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String typeAliasLibFolder = PKG_PREFIX + "/typeargument_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path typeAliasLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(typeAliasLibFolder, "lib"))
              .addSourceFiles(getKotlinFileInTest(typeAliasLibFolder, "lib_minified"))
              .compile();
      jarMap.put(targetVersion, typeAliasLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = jarMap.get(targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/typeargument_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".typeargument_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInTypeAlias_keepAll() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(jarMap.get(targetVersion))
            .addKeepAllClassesRule()
            .addKeepAttributes(
                ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS,
                ProguardKeepAttributes.SIGNATURE,
                ProguardKeepAttributes.INNER_CLASSES,
                ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            // TODO(b/151925520): Add inspections when program compiles correctly.
            // TODO(mkroghj): Also inspect the renaming of lib_minified
            //  (not now, but when program compiles correctly).
            .writeToZip();

    Path mainJar =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/typeargument_app", "main"))
            .compile();

    // TODO(b/152306391): Reified type-parameters are not flagged correctly.
    testForJvm()
        .addProgramFiles(mainJar)
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addRunClasspathFiles(libJar)
        .run(parameters.getRuntime(), PKG + ".typeargument_app.MainKt")
        .assertFailureWithErrorThatMatches(
            containsString(
                "This function has a reified type parameter and thus can only be inlined at"
                    + " compilation time, not called directly"));
  }
}
