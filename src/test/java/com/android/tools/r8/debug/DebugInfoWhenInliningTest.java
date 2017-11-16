// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/** Tests source file and line numbers on inlined methods. */
public class DebugInfoWhenInliningTest extends DebugTestBase {

  public static final String SOURCE_FILE = "Inlining1.java";
  private static DebuggeePath debuggeePathNotOptimized;
  private static DebuggeePath debuggeePathOptimized;

  private static DebuggeePath makeDex(LineNumberOptimization lineNumberOptimization)
      throws Exception {
    return DebuggeePath.makeDex(
        compileToDexViaR8(
            oc -> {
              oc.lineNumberOptimization = lineNumberOptimization;
            },
            null,
            DEBUGGEE_JAR,
            Collections.<String>emptyList(),
            true,
            CompilationMode.RELEASE));
  }

  @BeforeClass
  public static void initDebuggeePath() throws Exception {
    debuggeePathNotOptimized = makeDex(LineNumberOptimization.OFF);
    debuggeePathOptimized = makeDex(LineNumberOptimization.ON);
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
    testEachLine(debuggeePathNotOptimized, lineNumbers);
  }

  @Test
  public void testEachLineOptimized() throws Throwable {
    int[] lineNumbers = {1, 2, 3, 4, 5, 6, 7, 8};
    testEachLine(debuggeePathOptimized, lineNumbers);
  }

  private void testEachLine(DebuggeePath debuggeePath, int[] lineNumbers) throws Throwable {
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
    runDebugTest(debuggeePath, className, commands);
  }
}
