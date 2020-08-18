// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinMetadataTest extends DesugaredLibraryTestBase {

  private static final String PKG = KotlinMetadataTest.class.getPackage().getName();
  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private final KotlinTargetVersion targetVersion;
  private static final String EXPECTED_OUTPUT = "Wuhuu, my special day is: 1997-8-29-2-14";

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}, target: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        KotlinTargetVersion.values());
  }

  public KotlinMetadataTest(
      boolean shrinkDesugaredLibrary,
      TestParameters parameters,
      KotlinTargetVersion targetVersion) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
    this.targetVersion = targetVersion;
  }

  private static Map<KotlinTargetVersion, Path> compiledJars = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      compiledJars.put(
          targetVersion,
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(
                  Paths.get(
                      ToolHelper.TESTS_DIR,
                      "java",
                      DescriptorUtils.getBinaryNameFromJavaType(PKG),
                      "Main" + FileUtils.KT_EXTENSION))
              .compile());
    }
  }

  @Test
  public void testCf() throws Exception {
    assumeTrue(parameters.getRuntime().isCf());
    testForRuntime(parameters)
        .addProgramFiles(compiledJars.get(targetVersion))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addProgramFiles(ToolHelper.getKotlinReflectJar())
        .run(parameters.getRuntime(), PKG + ".MainKt")
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testTimeD8() throws Exception {
    assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    final File output = temp.newFile("output.zip");
    testForD8()
        .addProgramFiles(compiledJars.get(targetVersion))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addProgramFiles(ToolHelper.getKotlinReflectJar())
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
            this::buildDesugaredLibrary, parameters.getApiLevel(), keepRuleConsumer.get(), false)
        .run(parameters.getRuntime(), PKG + ".MainKt")
        .assertFailureWithErrorThatMatches(containsString("java.lang.ClassNotFoundException"));
  }

  @Test
  public void testTimeR8() throws Exception {
    assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addProgramFiles(compiledJars.get(targetVersion))
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addProgramFiles(ToolHelper.getKotlinReflectJar())
        .addKeepMainRule(PKG + ".MainKt")
        .addKeepAllClassesRule()
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), PKG + ".MainKt")
        .assertFailureWithErrorThatMatches(containsString("java.lang.ClassNotFoundException"));
  }
}
