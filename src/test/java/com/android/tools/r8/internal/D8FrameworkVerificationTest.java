// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.VmTestRunner.IgnoreIfVmOlderThan;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class D8FrameworkVerificationTest extends CompilationTestBase {
  private static final int MIN_SDK = AndroidApiLevel.N.getLevel();
  private static final String JAR = "third_party/framework/framework_160115954.jar";

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void verifyDebugBuild() throws Exception {
    runAndCheckVerification(
        D8Command.builder()
            .addProgramFiles(Paths.get(JAR))
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(MIN_SDK),
        JAR);
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void verifyReleaseBuild() throws Exception {
    runAndCheckVerification(
        D8Command.builder()
            .addProgramFiles(Paths.get(JAR))
            .setMode(CompilationMode.RELEASE)
            .setMinApiLevel(MIN_SDK),
        JAR);
  }
}
