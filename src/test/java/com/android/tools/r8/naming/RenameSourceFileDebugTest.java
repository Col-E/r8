// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.ClassNameMapper.MissingFileAction.MISSING_FILE_IS_EMPTY_MAP;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.CfDebugTestConfig;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.debug.DexDebugTestConfig;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests -renamesourcefileattribute. */
@RunWith(Parameterized.class)
public class RenameSourceFileDebugTest extends DebugTestBase {

  private static final String TEST_FILE = "TestFile.java";

  private static Map<Backend, DebugTestConfig> configs = new HashMap<>();

  @BeforeClass
  public static void initDebuggeePath() throws Exception {
    for (Backend backend : Backend.values()) {
      Path outdir = temp.newFolder().toPath();
      Path outjar = outdir.resolve("r8_compiled.jar");
      Path proguardMapPath = outdir.resolve("proguard.map");
      R8Command.Builder builder =
          ToolHelper.addProguardConfigurationConsumer(
                  R8Command.builder(),
                  pgConfig -> {
                    pgConfig.setRenameSourceFileAttribute(TEST_FILE);
                    pgConfig.addKeepAttributePatterns(
                        ImmutableList.of("SourceFile", "LineNumberTable"));
                  })
              .addProgramFiles(DEBUGGEE_JAR)
              .setMode(CompilationMode.DEBUG)
              .setProguardMapOutputPath(proguardMapPath);
      DebugTestConfig config;
      if (backend == Backend.DEX) {
        AndroidApiLevel minSdk = ToolHelper.getMinApiLevelForDexVm();
        builder
            .setMinApiLevel(minSdk.getLevel())
            .addLibraryFiles(ToolHelper.getAndroidJar(minSdk))
            .setOutput(outjar, OutputMode.DexIndexed);
        config = new DexDebugTestConfig(outjar);
      } else {
        assert backend == Backend.CF;
        builder
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setOutput(outjar, OutputMode.ClassFile);
        config = new CfDebugTestConfig(outjar);
      }
      ToolHelper.runR8(builder.build());
      config.setProguardMap(proguardMapPath, MISSING_FILE_IS_EMPTY_MAP);
      configs.put(backend, config);
    }
  }

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public RenameSourceFileDebugTest(Backend backend) {
    this.backend = backend;
  }

  /**
   * replica of {@link
   * com.android.tools.r8.debug.ClassInitializationTest#testBreakpointInEmptyClassInitializer}
   */
  @Test
  public void testBreakpointInEmptyClassInitializer() throws Throwable {
    final String CLASS = "ClassInitializerEmpty";
    runDebugTest(
        configs.get(backend),
        CLASS,
        breakpoint(CLASS, "<clinit>"),
        run(),
        checkLine(TEST_FILE, 8),
        run());
  }

  /**
   * replica of {@link com.android.tools.r8.debug.LocalsTest#testNoLocal}, except for checking
   * overwritten class file.
   */
  @Test
  public void testNoLocal() throws Throwable {
    final String className = "Locals";
    final String methodName = "noLocals";
    runDebugTest(
        configs.get(backend),
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

  /** replica of {@link com.android.tools.r8.debug.MultipleReturnsTest#testMultipleReturns} */
  @Test
  public void testMultipleReturns() throws Throwable {
    runDebugTest(
        configs.get(backend),
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
