// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.origin.Origin;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class InterfaceRenamingTestRunner extends TestBase {
  static final Class CLASS = InterfaceRenamingTest.class;
  static final Class[] CLASSES = InterfaceRenamingTest.CLASSES;

  @Test
  public void testCfNoMinify() throws Exception {
    testCf(MinifyMode.NONE, false);
  }

  @Test
  public void testCfMinify() throws Exception {
    testCf(MinifyMode.JAVA, false);
  }

  @Test
  public void testCfMinify_useUniqueClassMemberNames() throws Exception {
    testCf(MinifyMode.JAVA, true);
  }

  @Test
  public void testCfMinifyAggressive() throws Exception {
    testCf(MinifyMode.AGGRESSIVE, false);
  }

  @Test
  public void testCfMinifyAggressive_useUniqueClassMemberNames() throws Exception {
    testCf(MinifyMode.AGGRESSIVE, true);
  }

  @Test
  public void testDexNoMinify() throws Exception {
    testDex(MinifyMode.NONE, false);
  }

  @Test
  public void testDexMinify() throws Exception {
    testDex(MinifyMode.JAVA, false);
  }

  @Test
  public void testDexMinify_useUniqueClassMemberNames() throws Exception {
    testDex(MinifyMode.JAVA, true);
  }

  @Test
  public void testDexMinifyAggressive() throws Exception {
    testDex(MinifyMode.AGGRESSIVE, false);
  }

  @Test
  public void testDexMinifyAggressive_useUniqueClassMemberNames() throws Exception {
    testDex(MinifyMode.AGGRESSIVE, true);
  }

  private void testCf(MinifyMode minify, boolean useUniqueClassMemberNames) throws Exception {
    ProcessResult runInput =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    Path outCf = temp.getRoot().toPath().resolve("cf.zip");
    build(new ClassFileConsumer.ArchiveConsumer(outCf), minify, useUniqueClassMemberNames);
    ProcessResult runCf = ToolHelper.runJava(outCf, CLASS.getCanonicalName());
    assertEquals(runInput.toString(), runCf.toString());
  }

  private void testDex(MinifyMode minify, boolean useUniqueClassMemberNames) throws Exception {
    ProcessResult runInput =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    build(new DexIndexedConsumer.ArchiveConsumer(outDex), minify, useUniqueClassMemberNames);
    ProcessResult runDex =
        ToolHelper.runArtNoVerificationErrorsRaw(outDex.toString(), CLASS.getCanonicalName());
    assertEquals(runInput.stdout, runDex.stdout);
    assertEquals(runInput.exitCode, runDex.exitCode);
  }

  private void build(
      ProgramConsumer consumer,
      MinifyMode minify,
      boolean useUniqueClassMemberNames) throws Exception {
    List<String> config =
        Arrays.asList(
            "-keep public class " + CLASS.getCanonicalName() + " {",
            "  public static void main(...);",
            "}");

    Builder builder =
        ToolHelper.addProguardConfigurationConsumer(
                R8Command.builder(),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setOverloadAggressively(minify == MinifyMode.AGGRESSIVE);
                  pgConfig.setUseUniqueClassMemberNames(useUniqueClassMemberNames);
                  if (!minify.isMinify()) {
                    pgConfig.disableObfuscation();
                  }
                })
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
            .setProgramConsumer(consumer)
            .addProguardConfiguration(config, Origin.unknown());
    for (Class<?> c : CLASSES) {
      builder.addClassProgramData(ToolHelper.getClassAsBytes(c), Origin.unknown());
    }
    ToolHelper.runR8(builder.build());
  }
}
