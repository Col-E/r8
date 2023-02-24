// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import static com.android.tools.r8.KotlinTestBase.getCompileMemoizer;
import static com.android.tools.r8.TestBase.memoizeBiFunction;

import com.android.tools.r8.KotlinCompilerTool;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestBase.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.BiFunction;

/** Shared test configuration for D8 compiled resources from the "kotlinR8TestResources/loops". */
class KotlinLoopD8Config extends D8DebugTestConfig {

  static final KotlinCompileMemoizer compiledKotlinJars =
      getCompileMemoizer(KotlinTestBase.getKotlinFilesInResource("loops"))
          .configure(KotlinCompilerTool::includeRuntime);

  private static final BiFunction<KotlinTestParameters, AndroidApiLevel, Path>
      compiledResourcesMemoized = memoizeBiFunction(KotlinLoopD8Config::getCompiledResources);

  private static Path getCompiledResources(
      KotlinTestParameters kotlinTestParameters, AndroidApiLevel apiLevel) throws IOException {
    Path outputPath =
        TestBase.getStaticTemp().newFolder().toPath().resolve("d8_debug_test_resources_kotlin.jar");
    Path kotlinJar = compiledKotlinJars.getForConfiguration(kotlinTestParameters);
    D8DebugTestConfig.d8Compile(Collections.singletonList(kotlinJar), apiLevel, null)
        .writeForTesting(outputPath, OutputMode.DexIndexed);
    return outputPath;
  }

  public static KotlinLoopD8Config build(
      KotlinTestParameters kotlinTestParameters, AndroidApiLevel apiLevel) {
    try {
      KotlinLoopD8Config kotlinDebugD8Config = new KotlinLoopD8Config();
      kotlinDebugD8Config.addPaths(compiledResourcesMemoized.apply(kotlinTestParameters, apiLevel));
      return kotlinDebugD8Config;
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
