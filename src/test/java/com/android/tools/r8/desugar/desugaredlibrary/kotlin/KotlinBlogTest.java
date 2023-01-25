// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static com.android.tools.r8.KotlinTestBase.getCompileMemoizer;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinTestBase.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinBlogTest extends DesugaredLibraryTestBase {

  private static final String PKG = KotlinBlogTest.class.getPackage().getName();
  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "Content: first second",
          "Size: 12",
          "Exists (before deletion): true",
          "Exists (after deletion): false");

  private final KotlinTestParameters kotlinParameters;
  private final KotlinCompiler kotlinc;

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, kotlin: {1}, spec: {2}, {3}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        ImmutableList.of(LibraryDesugaringSpecification.JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public KotlinBlogTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.kotlinParameters = kotlinParameters;
    this.kotlinc = kotlinParameters.getCompiler();
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void test() throws Throwable {
    if (parameters.getRuntime().isCf()) {
      testForRuntime(parameters)
          .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
          .addProgramFiles(kotlinc.getKotlinStdlibJar())
          .addProgramFiles(kotlinc.getKotlinReflectJar())
          .run(parameters.getRuntime(), PKG + ".BlogKt")
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      return;
    }
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinReflectJar())
        .applyIf(
            compilationSpecification.isProgramShrink(),
            builder -> builder.addProgramFiles(kotlinc.getKotlinAnnotationJar()))
        .addOptionsModification(
            options -> {
              options.testing.enableD8ResourcesPassThrough = true;
              options.dataResourceConsumer = options.programConsumer.getDataResourceConsumer();
            })
        .addKeepMainRule(PKG + ".BlogKt")
        .addKeepAllClassesRule()
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .allowDiagnosticMessages()
        .allowUnusedDontWarnKotlinReflectJvmInternal(
            kotlinParameters.getCompiler().isNot(KOTLINC_1_3_72))
        .compile()
        .withArt6Plus64BitsLib()
        .run(parameters.getRuntime(), PKG + ".BlogKt")
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private static KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(
          Paths.get(
              ToolHelper.TESTS_DIR,
              "java",
              DescriptorUtils.getBinaryNameFromJavaType(PKG),
              "Blog" + FileUtils.KT_EXTENSION));
}
