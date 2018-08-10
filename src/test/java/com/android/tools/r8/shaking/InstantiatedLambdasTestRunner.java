// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;

public class InstantiatedLambdasTestRunner extends TestBase {
  static final Class CLASS = InstantiatedLambdasTest.class;
  static final Class[] CLASSES = InstantiatedLambdasTest.CLASSES;

  private Path inputJar;
  private ProcessResult runInput;

  @Before
  public void writeAndRunInputJar() throws IOException {
    inputJar = temp.getRoot().toPath().resolve("input.jar");
    ArchiveConsumer buildInput = new ArchiveConsumer(inputJar);
    for (Class clazz : CLASSES) {
      buildInput.accept(
          ByteDataView.of(ToolHelper.getClassAsBytes(clazz)),
          DescriptorUtils.javaTypeToDescriptor(clazz.getName()),
          null);
    }
    buildInput.finished(null);
    runInput = ToolHelper.runJava(inputJar, CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
  }

  private Path writeProguardRules(boolean aggressive) throws IOException {
    Path pgConfig = temp.getRoot().toPath().resolve("keep.txt");
    FileUtils.writeTextFile(
        pgConfig,
        "-keep public class " + CLASS.getCanonicalName() + " {",
        "  public static void main(...);",
        "}",
        aggressive ? "-overloadaggressively" : "# Not overloading aggressively");
    return pgConfig;
  }

  @Test
  public void testProguard() throws Exception {
    buildAndRunProguard("pg.jar", false);
  }

  @Test
  public void testProguardAggressive() throws Exception {
    buildAndRunProguard("pg-aggressive.jar", true);
  }

  @Test
  public void testCf() throws Exception {
    buildAndRunCf("cf.zip", false);
  }

  @Test
  public void testCfAggressive() throws Exception {
    buildAndRunCf("cf-aggressive.zip", true);
  }

  @Test
  public void testDex() throws Exception {
    buildAndRunDex("dex.zip", false);
  }

  @Test
  public void testDexAggressive() throws Exception {
    buildAndRunDex("dex-aggressive.zip", true);
  }

  private void buildAndRunCf(String outName, boolean aggressive) throws Exception {
    Path outCf = temp.getRoot().toPath().resolve(outName);
    build(new ClassFileConsumer.ArchiveConsumer(outCf), aggressive);
    ProcessResult runCf = ToolHelper.runJava(outCf, CLASS.getCanonicalName());
    assertEquals(runInput.toString(), runCf.toString());
  }

  private void buildAndRunDex(String outName, boolean aggressive) throws Exception {
    Path outDex = temp.getRoot().toPath().resolve(outName);
    build(new DexIndexedConsumer.ArchiveConsumer(outDex), aggressive);
    ProcessResult runDex =
        ToolHelper.runArtNoVerificationErrorsRaw(outDex.toString(), CLASS.getCanonicalName());
    assertEquals(runInput.stdout, runDex.stdout);
    assertEquals(runInput.exitCode, runDex.exitCode);
  }

  private void build(ProgramConsumer consumer, boolean aggressive) throws Exception {
    Builder builder =
        ToolHelper.addProguardConfigurationConsumer(
                R8Command.builder(), configuration -> configuration.setPrintMapping(true))
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
            .addProgramFiles(inputJar)
            .setProgramConsumer(consumer)
            .addProguardConfigurationFiles(writeProguardRules(aggressive));
    if (!(consumer instanceof ClassFileConsumer)) {
      builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    }
    ToolHelper.runR8(builder.build());
  }

  private void buildAndRunProguard(String outName, boolean aggressive) throws Exception {
    Path pgConfig = writeProguardRules(aggressive);
    Path outPg = temp.getRoot().toPath().resolve(outName);
    ProcessResult proguardResult =
        ToolHelper.runProguard6Raw(
            inputJar, outPg, ToolHelper.getJava8RuntimeJar(), pgConfig, null);
    System.out.println(proguardResult.stdout);
    if (proguardResult.exitCode != 0) {
      System.out.println(proguardResult.stderr);
    }
    assertEquals(0, proguardResult.exitCode);
    ProcessResult runPg = ToolHelper.runJava(outPg, CLASS.getCanonicalName());
    assertEquals(0, runPg.exitCode);
  }
}
