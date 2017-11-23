// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.OutputMode;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.rules.TemporaryFolder;

// Shared test configuration for D8 compiled resources from the "debugTestResources" target.
public class D8DebugTestResourcesConfig extends D8BaseDebugTestConfig {

  private static AndroidApp compiledResources = null;

  private static synchronized AndroidApp getCompiledResources() throws Throwable {
    if (compiledResources == null) {
      int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
      compiledResources =
          ToolHelper.runD8(
              D8Command.builder()
                  .addProgramFiles(DebugTestBase.DEBUGGEE_JAR)
                  .setMinApiLevel(minSdk)
                  .setMode(CompilationMode.DEBUG)
                  .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
                  .build(),
              null);
    }
    return compiledResources;
  }

  public D8DebugTestResourcesConfig(TemporaryFolder temp) {
    super(temp);
    try {
      Path path = temp.newFolder().toPath().resolve("d8_debug_test_resources.jar");
      getCompiledResources().write(path, OutputMode.Indexed);
      addPaths(path);
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
