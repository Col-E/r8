// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.debug.DexDebugTestConfig;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests -renamesourcefileattribute.
 */
public class RenameSourceFileDebugTest extends DebugTestBase {

  private static final String TEST_FILE = "TestFile.java";

  private static DebugTestConfig config;

  @BeforeClass
  public static void initDebuggeePath() throws Exception {
    int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
    Path outdir = temp.newFolder().toPath();
    Path outjar = outdir.resolve("r8_compiled.jar");
    Path proguardMapPath = outdir.resolve("proguard.map");
    ToolHelper.runR8(
        R8Command.builder()
            .addProgramFiles(DEBUGGEE_JAR)
            .setMinApiLevel(minSdk)
            .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
            .setMode(CompilationMode.DEBUG)
            .setOutputPath(outjar)
            .setProguardMapOutput(proguardMapPath)
            .addProguardConfigurationConsumer(
                pg -> {
                  pg.setRenameSourceFileAttribute(TEST_FILE);
                  pg.addKeepAttributePatterns(ImmutableList.of("SourceFile", "LineNumberTable"));
                })
            .build());
    config = new DexDebugTestConfig(outjar);
    config.setProguardMap(proguardMapPath);
  }

  /**
   * replica of {@link com.android.tools.r8.debug.ClassInitializationTest#testBreakpointInEmptyClassInitializer}
   */
  @Test
  public void testBreakpointInEmptyClassInitializer() throws Throwable {
    final String CLASS = "ClassInitializerEmpty";
    runDebugTest(
        config, CLASS, breakpoint(CLASS, "<clinit>"), run(), checkLine(TEST_FILE, 8), run());
  }

  /**
   * replica of {@link com.android.tools.r8.debug.LocalsTest#testNoLocal},
   * except for checking overwritten class file.
   */
  @Test
  public void testNoLocal() throws Throwable {
    final String className = "Locals";
    final String methodName = "noLocals";
    runDebugTest(
        config,
        className,
        breakpoint(className, methodName),
        run(),
        checkMethod(className, methodName),
        checkLine(TEST_FILE, 8),
        checkNoLocal(),
        stepOver(),
        checkMethod(className, methodName),
        checkLine(TEST_FILE, 9),
        checkNoLocal(),
        run());
  }

  /**
   * replica of {@link com.android.tools.r8.debug.MultipleReturnsTest#testMultipleReturns}
   */
  @Test
  public void testMultipleReturns() throws Throwable {
    runDebugTest(
        config,
        "MultipleReturns",
        breakpoint("MultipleReturns", "multipleReturns"),
        run(),
        stepOver(),
        checkLine(TEST_FILE, 16), // this should be the 1st return statement
        run(),
        stepOver(),
        checkLine(TEST_FILE, 18), // this should be the 2nd return statement
        run());
  }
}
