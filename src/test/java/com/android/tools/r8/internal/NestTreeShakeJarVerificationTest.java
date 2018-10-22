// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class NestTreeShakeJarVerificationTest extends NestCompilationBase {

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    AndroidApp app =
        runAndCheckVerification(
            CompilerUnderTest.R8,
            CompilationMode.RELEASE,
            null,
            ImmutableList.of(BASE + PG_CONF, BASE + PG_CONF_NO_OPT),
            null,
            ImmutableList.of(BASE + DEPLOY_JAR));
  }
}
