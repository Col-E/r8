// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.rules.TemporaryFolder;

// Shared test configuration for R8/CF compiled resources from the "debugTestResources" target.
public class R8CfDebugTestResourcesConfig extends CfDebugTestConfig {

  private static AndroidApp compiledResources = null;

  private static synchronized AndroidApp getCompiledResources() throws Throwable {
    if (compiledResources == null) {
      int minApi = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
      AndroidAppConsumers sink = new AndroidAppConsumers();
      R8.run(
          R8Command.builder()
              .setMinApiLevel(minApi)
              .setMode(CompilationMode.DEBUG)
              .addProgramFiles(DebugTestBase.DEBUGGEE_JAR)
              .setProgramConsumer(sink.wrapClassFileConsumer(null))
              .addLibraryFiles(ToolHelper.getAndroidJar(minApi))
              .build());
      compiledResources = sink.build();
    }
    return compiledResources;
  }

  public R8CfDebugTestResourcesConfig(TemporaryFolder temp) {
    try {
      Path path = temp.newFolder().toPath().resolve("r8_cf_debug_test_resources.jar");
      getCompiledResources().write(path, OutputMode.ClassFile);
      addPaths(path);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
