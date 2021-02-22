// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import static com.android.tools.r8.TestBase.kotlinc;
import static com.android.tools.r8.TestBase.memoizeBiFunction;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.BiFunction;
import org.junit.rules.TemporaryFolder;

/** Shared test configuration for D8 compiled resources from the "kotlinR8TestResources/debug". */
class KotlinD8Config extends D8DebugTestConfig {

  public static BiFunction<TemporaryFolder, KotlinTestParameters, Path> compileKotlinMemoized =
      memoizeBiFunction(KotlinD8Config::compileWithKotlinC);

  private static Path compileWithKotlinC(TemporaryFolder temp, KotlinTestParameters parameters)
      throws IOException {
    return kotlinc(
            TestRuntime.getCheckedInJdk9(),
            temp,
            parameters.getCompiler(),
            parameters.getTargetVersion())
        .addSourceFiles(KotlinTestBase.getKotlinFilesInResource("debug"))
        .includeRuntime()
        .compile();
  }

  private static BiFunction<KotlinTestParameters, AndroidApiLevel, Path> compiledResourcesMemoized =
      memoizeBiFunction(KotlinD8Config::getCompiledResources);

  private static Path getCompiledResources(
      KotlinTestParameters kotlinTestParameters, AndroidApiLevel apiLevel) throws IOException {
    Path outputPath =
        TestBase.getStaticTemp().newFolder().toPath().resolve("d8_debug_test_resources_kotlin.jar");
    Path kotlinJar = compileKotlinMemoized.apply(TestBase.getStaticTemp(), kotlinTestParameters);
    D8DebugTestConfig.d8Compile(Collections.singletonList(kotlinJar), apiLevel, null)
        .write(outputPath, OutputMode.DexIndexed);
    return outputPath;
  }

  public static KotlinD8Config build(
      KotlinTestParameters kotlinTestParameters, AndroidApiLevel apiLevel) {
    try {
      KotlinD8Config kotlinD8Config = new KotlinD8Config();
      kotlinD8Config.addPaths(compiledResourcesMemoized.apply(kotlinTestParameters, apiLevel));
      return kotlinD8Config;
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
