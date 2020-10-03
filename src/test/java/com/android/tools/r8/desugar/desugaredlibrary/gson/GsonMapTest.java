// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.gson;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.gson.TestClasses.TestClass;
import com.android.tools.r8.utils.BooleanUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GsonMapTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final Path GSON_CONFIGURATION =
      Paths.get("src/test/java/com/android/tools/r8/desugar/desugaredlibrary/gson/gson.cfg");
  private static final Path GSON_2_8_1_JAR = Paths.get("third_party/iosched_2019/gson-2.8.1.jar");

  @Parameters(name = "shrinkDesugaredLibrary: {0}, runtime: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public GsonMapTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testGsonMap() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(TestClasses.class)
            .addProgramFiles(GSON_2_8_1_JAR)
            .addKeepMainRule(TestClass.class)
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
            .run(parameters.getRuntime(), TestClass.class);
    // TODO(b/167649682): Should be always true.
    // .assertSuccessWithOutputLines("true", "true", "true", "true", "true");
    if (shrinkDesugaredLibrary) {
      runResult.assertSuccessWithOutputLines("true", "true", "false", "false", "false");
    } else {
      runResult.assertSuccessWithOutputLines("true", "true", "false", "true", "false");
    }
  }
}
