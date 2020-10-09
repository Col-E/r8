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
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GsonAllMapsTest extends GsonDesugaredLibraryTestBase {
  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final String[] EXPECTED_RESULT =
      new String[] {
        "true", "true", "true", "true", "true", "true", "true", "true", "true", "true", "true",
        "true"
      };

  @Parameters(name = "shrinkDesugaredLibrary: {0}, runtime: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public GsonAllMapsTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testGsonMapD8() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(AllMapsTestClass.class)
        .addProgramFiles(GSON_2_8_1_JAR)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), AllMapsTestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_RESULT);
  }

  @Test
  public void testGsonMapR8() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(AllMapsTestClass.class)
        .addProgramFiles(GSON_2_8_1_JAR)
        .addKeepMainRule(AllMapsTestClass.class)
        .addKeepRuleFiles(GSON_CONFIGURATION)
        .allowUnusedProguardConfigurationRules()
        .allowDiagnosticMessages()
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), AllMapsTestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_RESULT);
  }
}
