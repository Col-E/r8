// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static com.android.tools.r8.utils.AssertionUtils.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;

public class R8GMSCoreV10TreeShakeJarVerificationTest
    extends R8GMSCoreTreeShakeJarVerificationTest {

  private String proguardMap1 = null;
  private String proguardMap2 = null;

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    Map<String, String> idsRoundOne = new ConcurrentHashMap<>();
    AndroidApp app1 =
        buildAndTreeShakeFromDeployJar(
            CompilationMode.RELEASE,
            GMSCORE_V10_DIR,
            false,
            GMSCORE_V10_MAX_SIZE,
            options -> {
              options.testing.processingContextsConsumer =
                  id -> assertNull(idsRoundOne.put(id, id));
              options.proguardMapConsumer =
                  ToolHelper.consumeString(proguardMap -> this.proguardMap1 = proguardMap);
            });
    Map<String, String> idsRoundTwo = new ConcurrentHashMap<>();
    AndroidApp app2 =
        buildAndTreeShakeFromDeployJar(
            CompilationMode.RELEASE,
            GMSCORE_V10_DIR,
            false,
            GMSCORE_V10_MAX_SIZE,
            options -> {
              options.testing.processingContextsConsumer =
                  id -> {
                    assertNotNull(idsRoundOne.get(id));
                    assertNull(idsRoundTwo.put(id, id));
                  };
              options.proguardMapConsumer =
                  ToolHelper.consumeString(proguardMap -> this.proguardMap2 = proguardMap);
            });

    // Verify that the result of the two compilations was the same.
    assertEquals(
        Collections.emptySet(),
        Sets.symmetricDifference(idsRoundOne.keySet(), idsRoundTwo.keySet()));
    assertIdenticalApplications(app1, app2);
    assertEquals(proguardMap1, proguardMap2);
  }
}
