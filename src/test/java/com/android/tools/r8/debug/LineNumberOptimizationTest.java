// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.DebugTestConfig.RuntimeKind;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests source file and line numbers on inlined methods. */
@RunWith(Parameterized.class)
public class LineNumberOptimizationTest extends DebugTestBase {

  private static final int[] ORIGINAL_LINE_NUMBERS = {20, 7, 8, 28, 8, 20, 21, 12, 21, 22, 16, 22};
  private static final int[] ORIGINAL_LINE_NUMBERS_DEBUG = {
    20, 7, 8, 28, 29, 9, 21, 12, 13, 22, 16, 17
  };
  private static final int[] OPTIMIZED_LINE_NUMBERS = {1, 1, 2, 1, 2, 1, 2, 3, 2, 3, 4, 3};
  private static final String CLASS1 = "LineNumberOptimization1";
  private static final String CLASS2 = "LineNumberOptimization2";
  private static final String FILE1 = CLASS1 + ".java";
  private static final String FILE2 = CLASS2 + ".java";
  private static final String MAIN_SIGNATURE = "([Ljava/lang/String;)V";

  private RuntimeKind runtimeKind;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> setup() {
    return ImmutableList.of(
        new Object[] {"CF", RuntimeKind.CF}, new Object[] {"DEX", RuntimeKind.DEX});
  }

  public LineNumberOptimizationTest(String name, RuntimeKind runtimeKind) {
    this.runtimeKind = runtimeKind;
  }

  private static DebugTestConfig makeConfig(
      LineNumberOptimization lineNumberOptimization,
      boolean writeProguardMap,
      boolean dontOptimizeByEnablingDebug,
      RuntimeKind runtimeKind)
      throws Exception {
    Path outdir = temp.newFolder().toPath();
    Path outjar = outdir.resolve("r8_compiled.jar");

    R8Command.Builder builder =
        R8Command.builder()
            .addProgramFiles(DEBUGGEE_JAR)
            .setMode(dontOptimizeByEnablingDebug ? CompilationMode.DEBUG : CompilationMode.RELEASE);
    DebugTestConfig config = null;

    if (runtimeKind == RuntimeKind.CF) {
      builder.setOutput(outjar, OutputMode.ClassFile);
      config = new CfDebugTestConfig(outjar);
    } else {
      assert (runtimeKind == RuntimeKind.DEX);
      AndroidApiLevel minSdk = ToolHelper.getMinApiLevelForDexVm();
      builder
          .setMinApiLevel(minSdk.getLevel())
          .addLibraryFiles(ToolHelper.getAndroidJar(minSdk))
          .setOutput(outjar, OutputMode.DexIndexed);
      config = new D8DebugTestConfig();
    }

    config.addPaths(outjar);
    if (writeProguardMap) {
      Path proguardMapPath = outdir.resolve("proguard.map");
      builder.setProguardMapOutputPath(proguardMapPath);
      config.setProguardMap(proguardMapPath);
    }

    ToolHelper.runR8(
        builder.build(),
        options -> {
          if (!dontOptimizeByEnablingDebug) {
            options.lineNumberOptimization = lineNumberOptimization;
          }
          options.enableInlining = false;
        });

    return config;
  }

  @Test
  public void testIdentityCompilation() throws Throwable {
    // Compilation will fail if the identity translation does.
    makeConfig(LineNumberOptimization.IDENTITY_MAPPING, true, false, runtimeKind);
  }

  @Test
  public void testNotOptimized() throws Throwable {
    testRelease(
        makeConfig(LineNumberOptimization.OFF, false, false, runtimeKind), ORIGINAL_LINE_NUMBERS);
  }

  @Test
  public void testNotOptimizedWithMap() throws Throwable {
    testRelease(
        makeConfig(LineNumberOptimization.OFF, true, false, runtimeKind), ORIGINAL_LINE_NUMBERS);
  }

  @Test
  public void testNotOptimizedByEnablingDebug() throws Throwable {
    testDebug(
        makeConfig(LineNumberOptimization.OFF, false, true, runtimeKind),
        ORIGINAL_LINE_NUMBERS_DEBUG);
  }

  @Test
  public void testNotOptimizedByEnablingDebugWithMap() throws Throwable {
    testDebug(
        makeConfig(LineNumberOptimization.OFF, true, true, runtimeKind),
        ORIGINAL_LINE_NUMBERS_DEBUG);
  }

  @Test
  public void testOptimized() throws Throwable {
    testRelease(
        makeConfig(LineNumberOptimization.ON, false, false, runtimeKind), OPTIMIZED_LINE_NUMBERS);
  }

  @Test
  public void testOptimizedWithMap() throws Throwable {
    testRelease(
        makeConfig(LineNumberOptimization.ON, true, false, runtimeKind), ORIGINAL_LINE_NUMBERS);
  }

  private void testDebug(DebugTestConfig config, int[] lineNumbers) throws Throwable {
    runDebugTest(
        config,
        CLASS1,
        breakpoint(CLASS1, "main", MAIN_SIGNATURE),
        run(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[0]),
        stepInto(),
        checkMethod(CLASS1, "callThisFromSameFile", "()V"),
        checkLine(FILE1, lineNumbers[1]),
        stepOver(),
        checkMethod(CLASS1, "callThisFromSameFile", "()V"),
        checkLine(FILE1, lineNumbers[2]),
        stepInto(INTELLIJ_FILTER),
        checkMethod(CLASS2, "callThisFromAnotherFile", "()V"),
        checkLine(FILE2, lineNumbers[3]),
        stepOver(),
        checkMethod(CLASS2, "callThisFromAnotherFile", "()V"),
        checkLine(FILE2, lineNumbers[4]),
        stepOver(),
        checkMethod(CLASS1, "callThisFromSameFile", "()V"),
        checkLine(FILE1, lineNumbers[5]),
        stepOver(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[6]),
        stepInto(),
        checkMethod(CLASS1, "callThisFromSameFile", "(I)V"),
        checkLine(FILE1, lineNumbers[7]),
        stepOver(),
        checkMethod(CLASS1, "callThisFromSameFile", "(I)V"),
        checkLine(FILE1, lineNumbers[8]),
        stepOver(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[9]),
        stepInto(),
        checkMethod(CLASS1, "callThisFromSameFile", "(II)V"),
        checkLine(FILE1, lineNumbers[10]),
        stepOver(),
        checkMethod(CLASS1, "callThisFromSameFile", "(II)V"),
        checkLine(FILE1, lineNumbers[11]),
        run());
  }

  // If we compile in release mode the line numbers are slightly different from the debug mode.
  // That's why we need a different set of checks for the release mode.
  //
  // In release mode void returns don't have line number information. On the other hand, because of
  // the line number information is moved as late as possible stepping in the debugger is different:
  // After a method call we step again on the invoke instructions's line number before moving onto
  // the next instruction.
  private void testRelease(DebugTestConfig config, int[] lineNumbers) throws Throwable {
    runDebugTest(
        config,
        CLASS1,
        breakpoint(CLASS1, "main", MAIN_SIGNATURE),
        run(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[0]),
        stepInto(),
        checkMethod(CLASS1, "callThisFromSameFile", "()V"),
        checkLine(FILE1, lineNumbers[1]),
        stepOver(),
        checkMethod(CLASS1, "callThisFromSameFile", "()V"),
        checkLine(FILE1, lineNumbers[2]),
        stepInto(INTELLIJ_FILTER),
        checkMethod(CLASS2, "callThisFromAnotherFile", "()V"),
        checkLine(FILE2, lineNumbers[3]),
        stepOver(),
        checkMethod(CLASS1, "callThisFromSameFile", "()V"),
        checkLine(FILE1, lineNumbers[4]),
        stepOver(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[5]),
        stepOver(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[6]),
        stepInto(),
        checkMethod(CLASS1, "callThisFromSameFile", "(I)V"),
        checkLine(FILE1, lineNumbers[7]),
        stepOver(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[8]),
        stepOver(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[9]),
        stepInto(),
        checkMethod(CLASS1, "callThisFromSameFile", "(II)V"),
        checkLine(FILE1, lineNumbers[10]),
        stepOver(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[11]),
        run());
  }
}
