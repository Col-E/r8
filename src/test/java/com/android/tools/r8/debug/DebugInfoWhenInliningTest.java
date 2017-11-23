// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Tests source file and line numbers on inlined methods. */
public class DebugInfoWhenInliningTest extends DebugTestBase {

  public static final String SOURCE_FILE = "Inlining1.java";

  private static DebugTestConfig makeConfig(LineNumberOptimization lineNumberOptimization)
      throws Exception {
    int minSdk = ToolHelper.getMinApiLevelForDexVm(ToolHelper.getDexVm());
    Path outdir = temp.newFolder().toPath();
    Path outjar = outdir.resolve("r8_compiled.jar");
    ToolHelper.runR8(
        R8Command.builder()
            .addProgramFiles(DEBUGGEE_JAR)
            .setMinApiLevel(minSdk)
            .addLibraryFiles(Paths.get(ToolHelper.getAndroidJar(minSdk)))
            .setMode(CompilationMode.RELEASE)
            .setOutputPath(outjar)
            .build(),
        options -> options.lineNumberOptimization = lineNumberOptimization);
    DebugTestConfig config = new D8DebugTestConfig(temp);
    config.addPaths(outjar);
    return config;
  }

  @Test
  public void testEachLineNotOptimized() throws Throwable {
    // The reason why the not-optimized test contains half as many line numbers as the optimized
    // one:
    //
    // In the Java source (Inlining1) each call is duplicated. Since they end up on the same line
    // (innermost callee) the line numbers are actually 7, 7, 32, 32, ... but even if the positions
    // are emitted duplicated in the dex code, the debugger stops only when there's a change.
    int[] lineNumbers = {7, 32, 11, 7};
    testEachLine(makeConfig(LineNumberOptimization.OFF), lineNumbers);
  }

  @Test
  public void testEachLineOptimized() throws Throwable {
    int[] lineNumbers = {1, 2, 3, 4, 5, 6, 7, 8};
    testEachLine(makeConfig(LineNumberOptimization.ON), lineNumbers);
  }

  private void testEachLine(DebugTestConfig config, int[] lineNumbers) throws Throwable {
    final String className = "Inlining1";
    final String mainSignature = "([Ljava/lang/String;)V";
    List<Command> commands = new ArrayList<Command>();
    commands.add(breakpoint(className, "main", mainSignature));
    commands.add(run());
    boolean first = true;
    for (int i : lineNumbers) {
      if (first) {
        first = false;
      } else {
        commands.add(stepOver());
      }
      commands.add(checkMethod(className, "main", mainSignature));
      commands.add(checkLine(SOURCE_FILE, i));
    }
    commands.add(run());
    runDebugTest(config, className, commands);
  }
}
