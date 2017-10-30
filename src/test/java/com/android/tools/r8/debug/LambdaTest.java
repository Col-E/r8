// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import org.junit.Assert;
import org.junit.Test;

// TODO(shertz) test local variables
public class LambdaTest extends DebugTestBase {

  public static final String SOURCE_FILE = "DebugLambda.java";
  private static final boolean RUN_JAVA = false;

  @Test
  public void testLambda_ExpressionOnSameLine() throws Throwable {
    String debuggeeClass = "DebugLambda";
    String initialMethodName = "printInt";
    runDebugTest(
        getDebuggeeJava8DexD8OrCf(RUN_JAVA),
        debuggeeClass,
        breakpoint(debuggeeClass, initialMethodName),
        run(),
        checkMethod(debuggeeClass, initialMethodName),
        checkLine(SOURCE_FILE, 12),
        stepInto(INTELLIJ_FILTER),
        checkLine(SOURCE_FILE, 16),
        run());
  }

  @Test
  public void testLambda_StatementOnNewLine() throws Throwable {
    String debuggeeClass = "DebugLambda";
    String initialMethodName = "printInt3";
    runDebugTest(
        getDebuggeeJava8DexD8OrCf(RUN_JAVA),
        debuggeeClass,
        breakpoint(debuggeeClass, initialMethodName),
        run(),
        checkMethod(debuggeeClass, initialMethodName),
        checkLine(SOURCE_FILE, 32),
        stepInto(INTELLIJ_FILTER),
        checkLine(SOURCE_FILE, 37),
        run());
  }

  @Test
  public void testLambda_StaticMethodReference_Trivial() throws Throwable {
    String debuggeeClass = "DebugLambda";
    String initialMethodName = "printInt2";
    runDebugTest(
        getDebuggeeJava8DexD8OrCf(RUN_JAVA),
        debuggeeClass,
        breakpoint(debuggeeClass, initialMethodName),
        run(),
        checkMethod(debuggeeClass, initialMethodName),
        checkLine(SOURCE_FILE, 20),
        stepInto(INTELLIJ_FILTER),
        RUN_JAVA ? LambdaTest::doNothing : stepInto(INTELLIJ_FILTER),
        checkMethod(debuggeeClass, "returnOne"),
        checkLine(SOURCE_FILE, 28),
        checkNoLocal(),
        run());
  }

  @Test
  public void testLambda_StaticMethodReference_NonTrivial() throws Throwable {
    String debuggeeClass = "DebugLambda";
    String initialMethodName = "testLambdaWithMethodReferenceAndConversion";
    runDebugTest(
        getDebuggeeJava8DexD8OrCf(RUN_JAVA),
        debuggeeClass,
        breakpoint(debuggeeClass, initialMethodName),
        run(),
        checkMethod(debuggeeClass, initialMethodName),
        checkLine(SOURCE_FILE, 46),
        stepInto(INTELLIJ_FILTER),
        inspect(t -> Assert.assertTrue(t.getMethodName().startsWith("lambda$"))),
        stepInto(INTELLIJ_FILTER),
        checkMethod(debuggeeClass, "concatObjects"),
        checkLine(SOURCE_FILE, 57),
        checkLocal("objects"),
        run());
  }

  private static void doNothing(JUnit3Wrapper jUnit3Wrapper) {
  }
}
