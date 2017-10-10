// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.Test;

public class D8FrameworkDeterministicTest extends CompilationTestBase {
  private static final int MIN_SDK = 24;
  private static final String JAR = "third_party/framework/framework_160115954.jar";

  private AndroidApp doRun(D8Command command) throws IOException, CompilationException {
    return ToolHelper.runD8(command);
  }

  @Test
  public void verifyDebugBuild() throws Exception {
    D8Command command = D8Command.builder()
        .addProgramFiles(Paths.get(JAR))
        .setMode(CompilationMode.DEBUG)
        .setMinApiLevel(MIN_SDK)
        .build();
    AndroidApp app1 = doRun(command);
    AndroidApp app2 = doRun(command);
    assertIdenticalApplications(app1, app2);
  }

  @Test
  public void verifyReleaseBuild() throws Exception {
    D8Command command = D8Command.builder()
        .addProgramFiles(Paths.get(JAR))
        .setMode(CompilationMode.RELEASE)
        .setMinApiLevel(MIN_SDK)
        .build();
    AndroidApp app1 = doRun(command);
    AndroidApp app2 = doRun(command);
    assertIdenticalApplications(app1, app2);
  }
}
