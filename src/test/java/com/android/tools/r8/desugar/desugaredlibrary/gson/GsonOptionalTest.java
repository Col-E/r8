// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.gson;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GsonOptionalTest extends GsonDesugaredLibraryTestBase {
  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameterized.Parameters(name = "shrinkDesugaredLibrary: {0}, runtime: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public GsonOptionalTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testGsonOptionalD8() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(OptionalTestClass.class)
        .addProgramFiles(GSON_2_8_1_JAR)
        .addOptionsModification(opt -> opt.ignoreMissingClasses = true)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), OptionalTestClass.class)
        .assertSuccessWithOutputLines("true", "true");
  }

  @Test
  public void testGsonOptionalR8() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(OptionalTestClass.class)
        .addProgramFiles(GSON_2_8_1_JAR)
        .addKeepMainRule(OptionalTestClass.class)
        .addKeepRuleFiles(GSON_CONFIGURATION)
        .allowUnusedProguardConfigurationRules()
        .addOptionsModification(opt -> opt.ignoreMissingClasses = true)
        .allowDiagnosticMessages()
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), OptionalTestClass.class)
        .assertSuccessWithOutputLines("true", "true");
  }
}
