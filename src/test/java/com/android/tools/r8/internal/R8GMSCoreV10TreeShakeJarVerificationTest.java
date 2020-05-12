// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Test;

public class R8GMSCoreV10TreeShakeJarVerificationTest
    extends R8GMSCoreTreeShakeJarVerificationTest {

  private String proguardMap1 = null;
  private String proguardMap2 = null;

  @Test
  public void buildAndTreeShakeFromDeployJar() throws Exception {
    // TODO(tamaskenez): set hasReference = true when we have the noshrink file for V10
    Map<String, IntSet> methodProcessingIds = new ConcurrentHashMap<>();
    AndroidApp app1 =
        buildAndTreeShakeFromDeployJar(
            CompilationMode.RELEASE,
            GMSCORE_V10_DIR,
            false,
            GMSCORE_V10_MAX_SIZE,
            options -> {
              options.testing.methodProcessingIdConsumer =
                  (method, methodProcessingId) ->
                      assertTrue(
                          methodProcessingIds
                              .computeIfAbsent(
                                  method.toSourceString(), ignore -> new IntOpenHashSet(4))
                              .add(methodProcessingId.getPrimaryId()));
              options.proguardMapConsumer =
                  ToolHelper.consumeString(proguardMap -> this.proguardMap1 = proguardMap);
            });
    AndroidApp app2 =
        buildAndTreeShakeFromDeployJar(
            CompilationMode.RELEASE,
            GMSCORE_V10_DIR,
            false,
            GMSCORE_V10_MAX_SIZE,
            options -> {
              options.testing.methodProcessingIdConsumer =
                  (method, methodProcessingId) -> {
                    String key = method.toSourceString();
                    IntSet ids = methodProcessingIds.get(key);
                    assertNotNull(ids);
                    assertTrue(ids.remove(methodProcessingId.getPrimaryId()));
                    if (ids.isEmpty()) {
                      methodProcessingIds.remove(key);
                    }
                  };
              options.proguardMapConsumer =
                  ToolHelper.consumeString(proguardMap -> this.proguardMap2 = proguardMap);
            });

    // Verify that the result of the two compilations was the same.
    assertIdenticalApplications(app1, app2);
    assertTrue(methodProcessingIds.isEmpty());
    assertEquals(proguardMap1, proguardMap2);
  }
}
