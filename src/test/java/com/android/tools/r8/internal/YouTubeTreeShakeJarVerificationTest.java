// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import org.junit.Test;

public class YouTubeTreeShakeJarVerificationTest extends YouTubeCompilationBase {

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    Path proguardMapPath = File.createTempFile("mapping", ".txt", temp.getRoot()).toPath();
    int maxSize = 20000000;
    AndroidApp app =
        runAndCheckVerification(
            CompilerUnderTest.R8,
            CompilationMode.RELEASE,
            BASE + APK,
            ImmutableList.of(BASE + PG_CONF),
            options -> options.proguardMapConsumer = new FileConsumer(proguardMapPath),
            // Don't pass any inputs. The input will be read from the -injars in the Proguard
            // configuration file.
            ImmutableList.of());
    int bytes = applicationSize(app);
    assertTrue("Expected max size of " + maxSize + ", got " + bytes, bytes < maxSize);
  }
}
