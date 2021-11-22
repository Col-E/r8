// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static com.android.tools.r8.KotlinTestBase.getCompileMemoizer;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinTestBase.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.kotlin.KotlinMetadataWriter;
import com.android.tools.r8.kotlin.metadata.KotlinMetadataTestBase;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinMetadataTest extends DesugaredLibraryTestBase {

  private static final String PKG = KotlinMetadataTest.class.getPackage().getName();
  private static final String EXPECTED_OUTPUT = "Wuhuu, my special day is: 1997-8-29-2-14";

  private final TestParameters parameters;
  private final KotlinTestParameters kotlinParameters;
  private final KotlinCompiler kotlinc;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{0}, {1}, shrinkDesugaredLibrary: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public KotlinMetadataTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean shrinkDesugaredLibrary) {
    this.parameters = parameters;
    this.kotlinParameters = kotlinParameters;
    this.kotlinc = kotlinParameters.getCompiler();
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
  }

  private static KotlinCompileMemoizer compiledJars =
      getCompileMemoizer(
          Paths.get(
              ToolHelper.TESTS_DIR,
              "java",
              DescriptorUtils.getBinaryNameFromJavaType(PKG),
              "Main" + FileUtils.KT_EXTENSION));

  @Test
  public void testCf() throws Exception {
    assumeTrue(parameters.getRuntime().isCf());
    testForRuntime(parameters)
        .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
        .addProgramFiles(kotlinc.getKotlinStdlibJar())
        .addProgramFiles(kotlinc.getKotlinReflectJar())
        .run(parameters.getRuntime(), PKG + ".MainKt")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testTimeD8() throws Exception {
    assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    final File output = temp.newFile("output.zip");
    final D8TestRunResult d8TestRunResult =
        testForD8()
            .addLibraryFiles(getLibraryFile())
            .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
            .addProgramFiles(kotlinc.getKotlinStdlibJar())
            .addProgramFiles(kotlinc.getKotlinReflectJar())
            .setProgramConsumer(new ArchiveConsumer(output.toPath(), true))
            .setMinApi(parameters.getApiLevel())
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .addOptionsModification(
                options -> {
                  options.testing.enableD8ResourcesPassThrough = true;
                  options.dataResourceConsumer = options.programConsumer.getDataResourceConsumer();
                })
            .compile()
            .addDesugaredCoreLibraryRunClassPath(
                this::buildDesugaredLibrary,
                parameters.getApiLevel(),
                keepRuleConsumer.get(),
                false)
            .run(parameters.getRuntime(), PKG + ".MainKt")
            .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
    if (requiresTimeDesugaring(parameters)) {
      d8TestRunResult.inspect(this::inspectRewrittenMetadata);
    }
  }

  @Test
  public void testTimeR8() throws Exception {
    boolean desugarLibrary = parameters.isDexRuntime() && requiresTimeDesugaring(parameters);
    final R8FullTestBuilder testBuilder =
        testForR8(parameters.getBackend())
            .addLibraryFiles(getLibraryFile())
            .addProgramFiles(compiledJars.getForConfiguration(kotlinParameters))
            .addProgramFiles(kotlinc.getKotlinStdlibJar())
            .addProgramFiles(kotlinc.getKotlinReflectJar())
            .addProgramFiles(kotlinc.getKotlinAnnotationJar())
            .addKeepMainRule(PKG + ".MainKt")
            .addKeepAllClassesRule()
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .setMinApi(parameters.getApiLevel())
            .allowDiagnosticMessages()
            .allowUnusedDontWarnKotlinReflectJvmInternal(
                kotlinParameters.getCompiler().isNot(KOTLINC_1_3_72));
    KeepRuleConsumer keepRuleConsumer = null;
    if (desugarLibrary) {
      keepRuleConsumer = createKeepRuleConsumer(parameters);
      testBuilder.enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer);
    }
    R8TestCompileResult compileResult =
        testBuilder
            .compile()
            .assertNoErrorMessages()
            // -keepattributes Signature is added in kotlin-reflect from version 1.4.20.
            .applyIf(
                kotlinParameters.getCompiler().isNot(KOTLINC_1_3_72),
                TestBase::verifyAllInfoFromGenericSignatureTypeParameterValidation,
                TestCompileResult::assertNoInfoMessages)
            .apply(KotlinMetadataTestBase::verifyExpectedWarningsFromKotlinReflectAndStdLib);
    if (desugarLibrary) {
      assertNotNull(keepRuleConsumer);
      compileResult.addDesugaredCoreLibraryRunClassPath(
          this::buildDesugaredLibrary,
          parameters.getApiLevel(),
          keepRuleConsumer.get(),
          shrinkDesugaredLibrary);
    }
    final R8TestRunResult r8TestRunResult =
        compileResult
            .run(parameters.getRuntime(), PKG + ".MainKt")
            .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
    if (desugarLibrary) {
      r8TestRunResult.inspect(this::inspectRewrittenMetadata);
    }
  }

  private void inspectRewrittenMetadata(CodeInspector inspector) {
    final ClassSubject clazz =
        inspector.clazz("com.android.tools.r8.desugar.desugaredlibrary.kotlin.Skynet");
    assertThat(clazz, isPresent());
    final KotlinClassMetadata kotlinClassMetadata = clazz.getKotlinClassMetadata();
    assertNotNull(kotlinClassMetadata);
    String metadata = KotlinMetadataWriter.kotlinMetadataToString("", kotlinClassMetadata);
    assertThat(metadata, containsString("specialDay:Lj$/time/LocalDateTime;"));
    assertThat(metadata, containsString("Class(name=j$/time/LocalDateTime)"));
    assertThat(metadata, not(containsString("java.time.LocalDateTime")));
  }
}
