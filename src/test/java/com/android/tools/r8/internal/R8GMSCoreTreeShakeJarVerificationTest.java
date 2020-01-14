// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class R8GMSCoreTreeShakeJarVerificationTest extends GMSCoreCompilationTestBase {

  public AndroidApp buildAndTreeShakeFromDeployJar(
      CompilationMode mode,
      String base,
      boolean hasReference,
      int maxSize,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    return buildAndTreeShakeFromDeployJar(
        mode, base, hasReference, maxSize, ImmutableList.of(), optionsConsumer);
  }

  public AndroidApp buildAndTreeShakeFromDeployJar(
      CompilationMode mode,
      String base,
      boolean hasReference,
      int maxSize,
      List<String> additionalProguardConfigurations,
      Consumer<InternalOptions> optionsConsumer)
      throws Exception {
    List<String> proguardConfigurations = new ArrayList<>(additionalProguardConfigurations);
    proguardConfigurations.add(base + PG_CONF);
    AndroidApp app =
        runAndCheckVerification(
            CompilerUnderTest.R8,
            mode,
            hasReference ? base + REFERENCE_APK : null,
            proguardConfigurations,
            optionsConsumer,
            // Don't pass any inputs. The input will be read from the -injars in the Proguard
            // configuration file.
            ImmutableList.of());
    int bytes = app.applicationSize();
    assertTrue("Expected max size of " + maxSize + ", got " + bytes, bytes < maxSize);
    return app;
  }
}
