// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Paths;
import org.junit.Test;

public class D8GMSCoreV10DeployJarVerificationTest extends GMSCoreDeployJarVerificationTest {

  @Test
  public void buildDebugFromDeployJar() throws Exception {
    buildFromDeployJar(
        CompilerUnderTest.D8, CompilationMode.DEBUG,
        GMSCoreCompilationTestBase.GMSCORE_V10_DIR, false);
  }

  @Test
  public void buildReleaseFromDeployJar() throws Exception {
    buildFromDeployJar(
        CompilerUnderTest.D8, CompilationMode.RELEASE,
        GMSCoreCompilationTestBase.GMSCORE_V10_DIR, false);
  }

  @Test
  public void testDeterminismDebugLegacyMultidexFromDeployJar() throws Exception {
    D8Command.Builder builder =
        D8Command.builder()
            .addProgramFiles(Paths.get(GMSCORE_V10_DIR + DEPLOY_JAR))
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K.getLevel()))
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .addMainDexListFiles(Paths.get(GMSCORE_V10_DIR + "main_dex_list.txt"));

    AndroidApp app1 = runAndCheckVerification(builder, null);
    D8Command.Builder builder2 =
        D8Command.builder()
            .addProgramFiles(Paths.get(GMSCORE_V10_DIR + DEPLOY_JAR))
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K.getLevel()))
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .addMainDexListFiles(Paths.get(GMSCORE_V10_DIR + "main_dex_list.txt"));

    AndroidApp app2 = runAndCheckVerification(builder2, null);
    // Verify that the result of the two compilations was the same.
    assertIdenticalApplications(app1, app2);
  }

  @Test
  public void buildDebugLegagyMultidexForDexOptFromDeployJar() throws Exception {
    D8Command.Builder builder =
        D8Command.builder()
            .addProgramFiles(Paths.get(GMSCORE_V10_DIR + DEPLOY_JAR))
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K.getLevel()))
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .setOptimizeMultidexForLinearAlloc(true)
            .addMainDexListFiles(Paths.get(GMSCORE_V10_DIR + "main_dex_list.txt"));

    runAndCheckVerification(builder, null);
  }

  @Test
  public void buildReleaseLegagyMultidexFromDeployJar() throws Exception {
    D8Command.Builder builder =
        D8Command.builder()
            .addProgramFiles(Paths.get(GMSCORE_V10_DIR + DEPLOY_JAR))
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K.getLevel()))
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .addMainDexListFiles(Paths.get(GMSCORE_V10_DIR + "main_dex_list.txt"));

    runAndCheckVerification(builder, null);
  }

  @Test
  public void buildReleaseLegagyMultidexForDexOptFromDeployJar() throws Exception {
    D8Command.Builder builder =
        D8Command.builder()
            .addProgramFiles(Paths.get(GMSCORE_V10_DIR + DEPLOY_JAR))
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.K.getLevel()))
            .setMode(CompilationMode.DEBUG)
            .setMinApiLevel(AndroidApiLevel.K.getLevel())
            .setOptimizeMultidexForLinearAlloc(true)
            .addMainDexListFiles(Paths.get(GMSCORE_V10_DIR + "main_dex_list.txt"));

    runAndCheckVerification(builder, null);
  }
}
