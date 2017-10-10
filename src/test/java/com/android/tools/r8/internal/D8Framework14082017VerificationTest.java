// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import java.nio.file.Paths;
import org.junit.Test;

public class D8Framework14082017VerificationTest extends CompilationTestBase {
  private static final int MIN_SDK = 24;
  private static final String JAR = "third_party/framework/framework_14082017.jar";

  @Test
  public void verifyDebugBuild() throws Exception {
    runAndCheckVerification(
        D8Command.builder()
            .addProgramFiles(Paths.get(JAR))
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(MIN_SDK)
            .build(),
        JAR);
  }

  @Test
  public void verifyReleaseBuild() throws Exception {
    runAndCheckVerification(
        D8Command.builder()
            .addProgramFiles(Paths.get(JAR))
            .setMode(CompilationMode.RELEASE)
            .setMinApiLevel(MIN_SDK)
            .build(),
        JAR);
  }
}
