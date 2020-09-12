// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class YouTubeProguardJarVerificationTest extends YouTubeCompilationBase {

  public YouTubeProguardJarVerificationTest() {
    super(12, 17);
  }

  @Test
  public void buildDebugFromProguardJar() throws Exception {
    runAndCheckVerification(
        CompilerUnderTest.R8,
        CompilationMode.DEBUG,
        base + APK,
        null,
        options -> options.testing.disableStackMapVerification = true,
        ImmutableList.of(base + PG_JAR));
  }

  @Test
  public void buildReleaseFromProguardJar() throws Exception {
    runAndCheckVerification(
        CompilerUnderTest.R8,
        CompilationMode.RELEASE,
        base + APK,
        null,
        options -> options.testing.disableStackMapVerification = true,
        ImmutableList.of(base + PG_JAR));
  }
}
