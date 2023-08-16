// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class D8FrameworkDeterministicTest extends CompilationTestBase {
  private static final int MIN_SDK = 24;
  private static final Path JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR).resolve("framework").resolve("framework_160115954.jar");

  private AndroidApp doRun(D8Command.Builder builder) throws CompilationFailedException {
    builder.setProgramConsumer(null);
    AndroidAppConsumers appSink = new AndroidAppConsumers(builder);
    D8.run(builder.build());
    return appSink.build();
  }

  @Test
  public void verifyDebugBuild() throws Exception {
    D8Command.Builder command =
        D8Command.builder()
            .addProgramFiles(JAR)
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(MIN_SDK);
    AndroidApp app1 = doRun(command);
    AndroidApp app2 = doRun(command);
    assertIdenticalApplications(app1, app2);
  }

  @Test
  public void verifyReleaseBuild() throws Exception {
    D8Command.Builder command =
        D8Command.builder()
            .addProgramFiles(JAR)
            .setMode(CompilationMode.RELEASE)
            .setMinApiLevel(MIN_SDK);
    AndroidApp app1 = doRun(command);
    AndroidApp app2 = doRun(command);
    assertIdenticalApplications(app1, app2);
  }
}
