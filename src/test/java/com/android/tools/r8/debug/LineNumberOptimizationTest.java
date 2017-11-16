// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import java.util.Collections;
import org.junit.BeforeClass;
import org.junit.Test;

/** Tests source file and line numbers on inlined methods. */
public class LineNumberOptimizationTest extends DebugTestBase {

  public static final String SOURCE_FILE = "LineNumberOptimization1.java";
  private static DebuggeePath debuggeePathOptimized;
  private static DebuggeePath debuggeePathNotOptimized;
  private static DebuggeePath debuggeePathIdentityTest;

  private final String class1 = "LineNumberOptimization1";
  private final String class2 = "LineNumberOptimization2";
  private final String file1 = class1 + ".java";
  private final String file2 = class2 + ".java";
  private final String mainSignature = "([Ljava/lang/String;)V";

  private static DebuggeePath makeDex(LineNumberOptimization lineNumberOptimization)
      throws Exception {
    return DebuggeePath.makeDex(
        compileToDexViaR8(
            oc -> {
              oc.lineNumberOptimization = lineNumberOptimization;
              oc.inlineAccessors = false;
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
    debuggeePathIdentityTest = makeDex(LineNumberOptimization.IDENTITY_MAPPING);
  }

  @Test
  public void testNotOptimized() throws Throwable {
    runDebugTest(
        debuggeePathNotOptimized,
        class1,
        breakpoint(class1, "main", mainSignature),
        run(),
        checkMethod(class1, "main", mainSignature),
        checkLine(file1, 12),
        stepInto(),
        checkMethod(class1, "callThisFromSameFile", "()V"),
        checkLine(file1, 7),
        stepOver(),
        checkMethod(class1, "callThisFromSameFile", "()V"),
        checkLine(file1, 8),
        stepInto(INTELLIJ_FILTER),
        checkMethod(class2, "callThisFromAnotherFile", "()V"),
        checkLine(file2, 28),
        run());
  }

  @Test
  public void testOptimized() throws Throwable {
    runDebugTest(
        debuggeePathOptimized,
        class1,
        breakpoint(class1, "main", mainSignature),
        run(),
        checkMethod(class1, "main", mainSignature),
        checkLine(file1, 12),
        stepInto(),
        checkMethod(class1, "callThisFromSameFile", "()V"),
        checkLine(file1, 7),
        stepOver(),
        checkMethod(class1, "callThisFromSameFile", "()V"),
        checkLine(file1, 8),
        stepInto(INTELLIJ_FILTER),
        checkMethod(class2, "callThisFromAnotherFile", "()V"),
        checkLine(file2, 28),
        run());
  }
}
