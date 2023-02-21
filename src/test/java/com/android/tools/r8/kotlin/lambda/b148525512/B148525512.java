// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b148525512;

import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B148525512 extends KotlinTestBase {

  private static final Package pkg = B148525512.class.getPackage();
  private static final String kotlinTestClassesPackage = pkg.getName();
  private static final String baseKtClassName = kotlinTestClassesPackage + ".BaseKt";
  private static final String featureKtClassNamet = kotlinTestClassesPackage + ".FeatureKt";
  private static final String baseClassName = kotlinTestClassesPackage + ".Base";

  private static final KotlinCompileMemoizer kotlinBaseClasses =
      getCompileMemoizer(getKotlinFileInTestPackage(pkg, "base"))
          .configure(
              kotlinCompilerTool -> kotlinCompilerTool.addClasspathFiles(getFeatureApiPath()));
  private static final KotlinCompileMemoizer kotlinFeatureClasses =
      getCompileMemoizer(getKotlinFileInTestPackage(pkg, "feature"))
          .configure(
              kotlinCompilerTool -> {
                // Compile the feature Kotlin code with the base classes on classpath.
                kotlinCompilerTool.addClasspathFiles(
                    kotlinBaseClasses.getForConfiguration(
                        kotlinCompilerTool.getCompiler(), kotlinCompilerTool.getTargetVersion()));
              });
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public B148525512(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static Path getFeatureApiPath() {
    try {
      Path featureApiJar = getStaticTemp().getRoot().toPath().resolve("feature_api.jar");
      if (Files.exists(featureApiJar)) {
        return featureApiJar;
      }
      writeClassesToJar(featureApiJar, FeatureAPI.class);
      return featureApiJar;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void test() throws Exception {
    Path featureCode = temp.newFile("feature.zip").toPath();
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(kotlinBaseClasses.getForConfiguration(kotlinc, targetVersion))
            .addProgramClasses(FeatureAPI.class)
            .addKeepMainRule(baseKtClassName)
            .addKeepClassAndMembersRules(baseClassName)
            .addKeepClassAndMembersRules(featureKtClassNamet)
            .addKeepClassAndMembersRules(FeatureAPI.class)
            .addHorizontallyMergedClassesInspector(
                inspector ->
                    inspector
                        .assertIsCompleteMergeGroup(
                            "com.android.tools.r8.kotlin.lambda.b148525512.BaseKt$main$1",
                            "com.android.tools.r8.kotlin.lambda.b148525512.BaseKt$main$2")
                        .assertIsCompleteMergeGroup(
                            "com.android.tools.r8.kotlin.lambda.b148525512.FeatureKt$feature$1",
                            "com.android.tools.r8.kotlin.lambda.b148525512.FeatureKt$feature$2"))
            .setMinApi(parameters)
            .addFeatureSplit(
                builder ->
                    builder
                        .addProgramResourceProvider(
                            ArchiveResourceProvider.fromArchive(
                                kotlinFeatureClasses.getForConfiguration(kotlinc, targetVersion),
                                true))
                        .setProgramConsumer(new ArchiveConsumer(featureCode, false))
                        .build())
            .allowDiagnosticWarningMessages()
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."));

    // Run the code without the feature code.
    compileResult
        .run(parameters.getRuntime(), baseKtClassName)
        .assertSuccessWithOutputLines("1", "2");

    // Run the code with the feature code present.
    compileResult
        .addRunClasspathFiles(featureCode)
        .run(parameters.getRuntime(), baseKtClassName)
        .assertSuccessWithOutputLines("1", "2", "3", "4");
  }
}
