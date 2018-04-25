// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.rules.TemporaryFolder;

/**
 * Shared test configuration for D8 compiled resources from the "debugTestResourcesKotlin" target.
 */
class KotlinD8Config extends D8DebugTestConfig {

  private static final Path DEBUGGEE_KOTLIN_JAR =
      Paths.get(ToolHelper.BUILD_DIR, "test", "debug_test_resources_kotlin.jar");

  private static AndroidApp compiledResources = null;

  private static synchronized AndroidApp getCompiledResources() throws Throwable {
    if (compiledResources == null) {
      compiledResources =
          D8DebugTestConfig.d8Compile(
              Collections.singletonList(DEBUGGEE_KOTLIN_JAR), null);
    }
    return compiledResources;
  }

  public KotlinD8Config(TemporaryFolder temp) {
    super();
    try {
      Path out = temp.newFolder().toPath().resolve("d8_debug_test_resources_kotlin.jar");
      getCompiledResources().write(out, OutputMode.DexIndexed);
      addPaths(out);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
