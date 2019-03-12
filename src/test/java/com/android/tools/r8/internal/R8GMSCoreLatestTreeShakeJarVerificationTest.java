// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

public class R8GMSCoreLatestTreeShakeJarVerificationTest
    extends R8GMSCoreTreeShakeJarVerificationTest {

  private String proguardMap1 = null;
  private String proguardMap2 = null;

  @Ignore("b/128416563")
  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    List<String> additionalProguardConfiguration =
        ImmutableList.of(
            ToolHelper.PROGUARD_SETTINGS_FOR_INTERNAL_APPS + "/GmsCore_proguard.config");
    AndroidApp app1 =
        buildAndTreeShakeFromDeployJar(
            CompilationMode.RELEASE,
            GMSCORE_LATEST_DIR,
            false,
            GMSCORE_LATEST_MAX_SIZE,
            additionalProguardConfiguration,
            options -> {
              options.proguardMapConsumer =
                  (proguardMap, handler) -> this.proguardMap1 = proguardMap;
            });
    AndroidApp app2 =
        buildAndTreeShakeFromDeployJar(
            CompilationMode.RELEASE,
            GMSCORE_LATEST_DIR,
            false,
            GMSCORE_LATEST_MAX_SIZE,
            additionalProguardConfiguration,
            options -> {
              options.proguardMapConsumer =
                  (proguardMap, handler) -> this.proguardMap2 = proguardMap;
            });

    // Verify that the result of the two compilations was the same.
    assertIdenticalApplications(app1, app2);
    assertEquals(proguardMap1, proguardMap2);
  }
}
