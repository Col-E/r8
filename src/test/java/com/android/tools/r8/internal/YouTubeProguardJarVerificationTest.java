// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import org.junit.Test;

public class YouTubeProguardJarVerificationTest extends YouTubeCompilationBase {

  @Test
  public void buildDebugFromProguardJar() throws Exception {
    runAndCheckVerification(
        CompilerUnderTest.R8, CompilationMode.DEBUG, BASE + APK, BASE + PG_MAP, null, BASE + PG_JAR);
  }

  @Test
  public void buildReleaseFromProguardJar() throws Exception {
    runAndCheckVerification(
        CompilerUnderTest.R8, CompilationMode.RELEASE,
        BASE + APK, BASE + PG_MAP, null, BASE + PG_JAR);
  }
}
