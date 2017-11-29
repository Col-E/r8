// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import org.junit.Test;

public class R8GMSCoreV9TreeShakeJarVerificationTest extends R8GMSCoreTreeShakeJarVerificationTest {

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    buildAndTreeShakeFromDeployJar(
        CompilationMode.RELEASE, GMSCORE_V9_DIR, true, GMSCORE_V9_MAX_SIZE, null);
  }
}
