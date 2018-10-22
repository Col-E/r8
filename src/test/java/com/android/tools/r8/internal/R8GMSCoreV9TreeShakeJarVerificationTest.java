// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.utils.AndroidApp;
import java.io.File;
import java.nio.file.Path;
import org.junit.Test;

public class R8GMSCoreV9TreeShakeJarVerificationTest extends R8GMSCoreTreeShakeJarVerificationTest {

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    Path proguardMapPath = File.createTempFile("mapping", ".txt", temp.getRoot()).toPath();
    AndroidApp app =
        buildAndTreeShakeFromDeployJar(
            CompilationMode.RELEASE,
            GMSCORE_V9_DIR,
            true,
            GMSCORE_V9_MAX_SIZE,
            options -> options.proguardMapConsumer = new FileConsumer(proguardMapPath));
  }
}
