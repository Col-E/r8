// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class R8GMSCoreV10DeployJarVerificationTest extends GMSCoreDeployJarVerificationTest {

  private String proguardMap1 = null;
  private String proguardMap2 = null;

  @Test
  public void buildFromDeployJar() throws Exception {
    // TODO(tamaskenez): set hasReference = true when we have the noshrink file for V10
    File tempFolder = temp.newFolder();

    File app1Zip = new File(tempFolder, "app1.zip");
    Map<String, IntList> methodProcessingIds = new HashMap<>();
    AndroidApp app1 =
        buildFromDeployJar(
            CompilerUnderTest.R8,
            CompilationMode.RELEASE,
            GMSCoreCompilationTestBase.GMSCORE_V10_DIR,
            false,
            options -> {
              options.testing.methodProcessingIdConsumer =
                  (method, methodProcessingId) ->
                      methodProcessingIds
                          .computeIfAbsent(method.toSourceString(), ignore -> new IntArrayList())
                          .add(methodProcessingId.getPrimaryId());
              options.proguardMapConsumer =
                  ToolHelper.consumeString(proguardMap -> this.proguardMap1 = proguardMap);
            },
            () -> new ArchiveConsumer(app1Zip.toPath(), true));

    File app2Zip = new File(tempFolder, "app2.zip");
    Map<String, IntList> otherMethodProcessingIds = new HashMap<>();
    AndroidApp app2 =
        buildFromDeployJar(
            CompilerUnderTest.R8,
            CompilationMode.RELEASE,
            GMSCoreCompilationTestBase.GMSCORE_V10_DIR,
            false,
            options -> {
              options.testing.methodProcessingIdConsumer =
                  (method, methodProcessingId) ->
                      otherMethodProcessingIds
                          .computeIfAbsent(method.toSourceString(), ignore -> new IntArrayList())
                          .add(methodProcessingId.getPrimaryId());
              options.proguardMapConsumer =
                  ToolHelper.consumeString(proguardMap -> this.proguardMap2 = proguardMap);
            },
            () -> new ArchiveConsumer(app2Zip.toPath(), true));

    // Verify that the result of the two compilations was the same.
    assertIdenticalApplications(app1, app2);
    assertIdenticalZipFiles(app1Zip, app2Zip);
    assertIdenticalMethodProcessingIds(methodProcessingIds, otherMethodProcessingIds);
    assertUniqueMethodProcessingIds(methodProcessingIds);
    assertEquals(proguardMap1, proguardMap2);
  }
}
