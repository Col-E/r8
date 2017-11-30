// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/** Tests source file and line numbers on inlined methods. */
public class LineNumberOptimizationTest extends DebugTestBase {

  private static final int[] ORIGINAL_LINE_NUMBERS = {20, 7, 8, 28, 8, 20, 21, 12, 21, 22, 16, 22};
  private static final int[] OPTIMIZED_LINE_NUMBERS = {1, 1, 2, 1, 2, 1, 2, 3, 2, 3, 4, 3};

  private static DebugTestConfig makeConfig(
      LineNumberOptimization lineNumberOptimization, boolean writeProguardMap) throws Exception {
    int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
    Path outdir = temp.newFolder().toPath();
    Path outjar = outdir.resolve("r8_compiled.jar");
    Path proguardMapPath = writeProguardMap ? outdir.resolve("proguard.map") : null;
    ToolHelper.runR8(
        R8Command.builder()
            .addProgramFiles(DEBUGGEE_JAR)
            .setMinApiLevel(minSdk)
            .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
            .setMode(CompilationMode.RELEASE)
            .setOutputPath(outjar)
            .setProguardMapOutput(proguardMapPath)
            .build(),
        options -> {
          options.lineNumberOptimization = lineNumberOptimization;
          options.inlineAccessors = false;
        });
    DebugTestConfig config = new D8DebugTestConfig();
    config.addPaths(outjar);
    config.setProguardMap(proguardMapPath);
    return config;
  }

  @Test
  public void testIdentityCompilation() throws Throwable {
    // Compilation will fail if the identity translation does.
    makeConfig(LineNumberOptimization.IDENTITY_MAPPING, true);
  }

  @Test
  public void testNotOptimized() throws Throwable {
    test(makeConfig(LineNumberOptimization.OFF, false), ORIGINAL_LINE_NUMBERS);
  }

  @Test
  public void testNotOptimizedWithMap() throws Throwable {
    test(makeConfig(LineNumberOptimization.OFF, true), ORIGINAL_LINE_NUMBERS);
  }

  @Test
  public void testOptimized() throws Throwable {
    test(makeConfig(LineNumberOptimization.ON, false), OPTIMIZED_LINE_NUMBERS);
  }

  @Test
  public void testOptimizedWithMap() throws Throwable {
    test(makeConfig(LineNumberOptimization.ON, true), ORIGINAL_LINE_NUMBERS);
  }

  private void test(DebugTestConfig config, int[] lineNumbers) throws Throwable {
    final String class1 = "LineNumberOptimization1";
    final String class2 = "LineNumberOptimization2";
    final String file1 = class1 + ".java";
    final String file2 = class2 + ".java";
    final String mainSignature = "([Ljava/lang/String;)V";

    runDebugTest(
        config,
        class1,
        breakpoint(class1, "main", mainSignature),
        run(),
        checkMethod(class1, "main", mainSignature),
        checkLine(file1, lineNumbers[0]),
        stepInto(),
        checkMethod(class1, "callThisFromSameFile", "()V"),
        checkLine(file1, lineNumbers[1]),
        stepOver(),
        checkMethod(class1, "callThisFromSameFile", "()V"),
        checkLine(file1, lineNumbers[2]),
        stepInto(INTELLIJ_FILTER),
        checkMethod(class2, "callThisFromAnotherFile", "()V"),
        checkLine(file2, lineNumbers[3]),
        stepOver(),
        checkMethod(class1, "callThisFromSameFile", "()V"),
        checkLine(file1, lineNumbers[4]),
        stepOver(),
        checkMethod(class1, "main", mainSignature),
        checkLine(file1, lineNumbers[5]),
        stepOver(),
        checkMethod(class1, "main", mainSignature),
        checkLine(file1, lineNumbers[6]),
        stepInto(),
        checkMethod(class1, "callThisFromSameFile", "(I)V"),
        checkLine(file1, lineNumbers[7]),
        stepOver(),
        checkMethod(class1, "main", mainSignature),
        checkLine(file1, lineNumbers[8]),
        stepOver(),
        checkMethod(class1, "main", mainSignature),
        checkLine(file1, lineNumbers[9]),
        stepInto(),
        checkMethod(class1, "callThisFromSameFile", "(II)V"),
        checkLine(file1, lineNumbers[10]),
        stepOver(),
        checkMethod(class1, "main", mainSignature),
        checkLine(file1, lineNumbers[11]),
        run());
  }
}
