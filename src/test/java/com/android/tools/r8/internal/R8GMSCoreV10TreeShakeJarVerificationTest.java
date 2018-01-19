// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.utils.AndroidApp;
import org.junit.Test;

public class R8GMSCoreV10TreeShakeJarVerificationTest
    extends R8GMSCoreTreeShakeJarVerificationTest {

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    // TODO(tamaskenez): set hasReference = true when we have the noshrink file for V10
    AndroidApp app1 = buildAndTreeShakeFromDeployJar(
        CompilationMode.RELEASE, GMSCORE_V10_DIR, false, GMSCORE_V10_MAX_SIZE, null);
    AndroidApp app2 = buildAndTreeShakeFromDeployJar(
        CompilationMode.RELEASE, GMSCORE_V10_DIR, false, GMSCORE_V10_MAX_SIZE, null);

    // Verify that the result of the two compilations was the same.
    assertIdenticalApplications(app1, app2);
  }
}
