// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.JvmTestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataPrimitiveTypeRewriteTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;
  private final String EXPECTED = "Hello World";
  private static final String PKG_LIB = PKG + ".primitive_type_rewrite_lib";
  private static final String PKG_APP = PKG + ".primitive_type_rewrite_app";

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataPrimitiveTypeRewriteTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

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
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testRenamingRenameUnit() throws Exception {
    runTest(false);
  }

  @Test
  public void testRenamingKeepUnit() throws Exception {
    runTest(true);
  }

  private void runTest(boolean keepUnit) throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(ToolHelper.getKotlinStdlibJar(), libJars.get(targetVersion))
            .addKeepAllClassesRuleWithAllowObfuscation()
            .addKeepRules("-keep class " + PKG_LIB + ".LibKt { *; }")
            .addKeepRules("-keep class kotlin.Metadata { *; }")
            .applyIf(keepUnit, b -> b.addKeepRules("-keep class kotlin.Unit { *; }"))
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .allowDiagnosticWarningMessages()
            .compile()
            .inspect(
                codeInspector -> {
                  final ClassSubject clazz = codeInspector.clazz("kotlin.Unit");
                  assertThat(clazz, isPresentAndRenamed(!keepUnit));
                })
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    final JvmTestRunResult runResult =
        testForJvm()
            .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
            .addClasspath(output)
            .run(parameters.getRuntime(), PKG_APP + ".MainKt");
    if (keepUnit) {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    } else {
      runResult.assertFailureWithErrorThatMatches(
          containsString(
              "java.lang.NoSuchMethodError:"
                  + " com.android.tools.r8.kotlin.metadata.primitive_type_rewrite_lib.LibKt.foo()"));
    }
  }
}
