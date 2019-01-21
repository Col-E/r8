// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import org.junit.Test;

public class R8GMSCoreV10JumboStringTest extends R8GMSCoreTreeShakeJarVerificationTest {

  @Test
  public void verify() throws Exception {
    buildAndTreeShakeFromDeployJar(
        CompilationMode.RELEASE,
        GMSCORE_V10_DIR,
        false,
        GMSCORE_V10_MAX_SIZE,
        options -> options.testing.forceJumboStringProcessing = true);
  }
}
