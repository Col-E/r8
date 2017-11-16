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
    int[] lineNumbers = {20, 7, 8, 28, 8, 20, 21, 12, 21, 22, 16, 22};
    test(debuggeePathNotOptimized, lineNumbers);
  }

  @Test
  public void testOptimized() throws Throwable {
    int[] lineNumbers = {1, 1, 2, 1, 2, 1, 2, 3, 2, 3, 4, 3};
    test(debuggeePathOptimized, lineNumbers);
  }

  private void test(DebuggeePath debuggeePath, int[] lineNumbers) throws Throwable {
    final String class1 = "LineNumberOptimization1";
    final String class2 = "LineNumberOptimization2";
    final String file1 = class1 + ".java";
    final String file2 = class2 + ".java";
    final String mainSignature = "([Ljava/lang/String;)V";

    runDebugTest(
        debuggeePath,
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
