// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInnerClassTest extends KotlinMetadataTestBase {

  private static final String PKG_NESTED_REFLECT = PKG + ".nested_reflect";
  private static final String EXPECTED =
      StringUtils.lines(
          "fun <init>(kotlin.Int): " + PKG_NESTED_REFLECT + ".Outer.Nested",
          "fun "
              + PKG_NESTED_REFLECT
              + ".Outer.Inner.<init>(kotlin.Int): "
              + PKG_NESTED_REFLECT
              + ".Outer.Inner");
  private static final String EXPECTED_OUTER_RENAMED =
      StringUtils.lines(
          "fun <init>(kotlin.Int): " + PKG_NESTED_REFLECT + ".`Outer$Nested`",
          "fun <init>(kotlin.Int): " + PKG_NESTED_REFLECT + ".`Outer$Inner`");

  private final TestParameters parameters;

  private String getExpected() {
    return replaceInitNameInExpectedBasedOnKotlinVersion(EXPECTED);
  }

  private String getExpectedOuterRenamed() {
    return replaceInitNameInExpectedBasedOnKotlinVersion(EXPECTED_OUTER_RENAMED);
  }

  private String replaceInitNameInExpectedBasedOnKotlinVersion(String expected) {
    return kotlinParameters.isNewerThanOrEqualTo(KotlinCompilerVersion.KOTLINC_1_7_0)
        ? expected.replace("<init>", "`<init>`")
        : expected;
  }

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataRewriteInnerClassTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer jarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/nested_reflect", "main"));

  @Test
  public void smokeTest() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    Path libJar = jarMap.getForConfiguration(kotlinc, targetVersion);
    testForRuntime(parameters)
        .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), libJar)
        .run(parameters.getRuntime(), PKG_NESTED_REFLECT + ".MainKt")
        .assertSuccessWithOutput(getExpected());
  }

  @Test
  public void testMetadataOuterRenamed() throws Exception {
    parameters.assumeR8TestParameters();
    Path mainJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar())
            .addClasspathFiles(kotlinc.getKotlinReflectJar())
            .addClasspathFiles(kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(jarMap.getForConfiguration(kotlinc, targetVersion))
            .addKeepRules("-keep public class " + PKG_NESTED_REFLECT + ".Outer$Nested { *; }")
            .addKeepRules("-keep public class " + PKG_NESTED_REFLECT + ".Outer$Inner { *; }")
            .addKeepMainRule(PKG_NESTED_REFLECT + ".MainKt")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .setMinApi(parameters)
            .compile()
            .inspect(inspector -> inspectPruned(inspector, true))
            .writeToZip();
    runD8(mainJar, getExpectedOuterRenamed());
  }

  @Test
  public void testMetadataOuterNotRenamed() throws Exception {
    parameters.assumeR8TestParameters();
    Path mainJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar())
            .addClasspathFiles(kotlinc.getKotlinReflectJar())
            .addClasspathFiles(kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(jarMap.getForConfiguration(kotlinc, targetVersion))
            .addKeepAttributeInnerClassesAndEnclosingMethod()
            .addKeepRules("-keep public class " + PKG_NESTED_REFLECT + ".Outer { *; }")
            .addKeepRules("-keep public class " + PKG_NESTED_REFLECT + ".Outer$Nested { *; }")
            .addKeepRules("-keep public class " + PKG_NESTED_REFLECT + ".Outer$Inner { *; }")
            .addKeepMainRule(PKG_NESTED_REFLECT + ".MainKt")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .setMinApi(parameters)
            .compile()
            .inspect(inspector -> inspectPruned(inspector, false))
            .writeToZip();
    runD8(mainJar, getExpected());
  }

  private void runD8(Path jar, String expected) throws Exception {
    Path output = temp.newFile("output.zip").toPath();
    ProgramConsumer programConsumer =
        parameters.isCfRuntime()
            ? new ClassFileConsumer.ArchiveConsumer(output, true)
            : new ArchiveConsumer(output, true);
    testForD8(parameters.getBackend())
        .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinReflectJar(), jar)
        .setMinApi(parameters)
        .setProgramConsumer(programConsumer)
        .addOptionsModification(
            options -> {
              // Needed for passing kotlin_builtin files to output.
              options.testing.enableD8ResourcesPassThrough = true;
              options.dataResourceConsumer = options.programConsumer.getDataResourceConsumer();
            })
        .run(parameters.getRuntime(), PKG_NESTED_REFLECT + ".MainKt")
        .assertSuccessWithOutput(expected);
  }

  private void inspectPruned(CodeInspector inspector, boolean outerRenamed) {
    assertThat(
        inspector.clazz(PKG_NESTED_REFLECT + ".Outer"),
        outerRenamed ? isPresentAndRenamed() : isPresent());
    assertThat(inspector.clazz(PKG_NESTED_REFLECT + ".Outer$Nested"), isPresent());
    assertThat(inspector.clazz(PKG_NESTED_REFLECT + ".Outer$Inner"), isPresent());
  }
}
