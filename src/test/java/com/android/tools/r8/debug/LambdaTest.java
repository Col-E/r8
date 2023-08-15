// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.classes.DebugLambda;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaTest extends DebugTestBase {

  private static final String DEBUGGEE_CLASS = typeName(DebugLambda.class);
  private static final String SOURCE_FILE = "DebugLambda.java";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public DebugTestConfig getConfig() throws Exception {
    return testForRuntime(parameters)
        .addProgramClassesAndInnerClasses(DebugLambda.class)
        .debugConfig(parameters.getRuntime());
  }

  @Test
  public void testLambda_ExpressionOnSameLine() throws Throwable {
    String initialMethodName = "printInt";
    runDebugTest(
        getConfig(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, initialMethodName),
        run(),
        checkMethod(DEBUGGEE_CLASS, initialMethodName),
        checkLine(SOURCE_FILE, 14),
        checkLocals("i"),
        checkNoLocal("j"),
        stepInto(INTELLIJ_FILTER),
        checkLine(SOURCE_FILE, 18),
        checkLocals("i", "j"),
        run());
  }

  @Test
  public void testLambda_StatementOnNewLine() throws Throwable {
    String initialMethodName = "printInt3";
    runDebugTest(
        getConfig(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, initialMethodName),
        run(),
        checkMethod(DEBUGGEE_CLASS, initialMethodName),
        checkLine(SOURCE_FILE, 34),
        checkLocals("i", "a", "b"),
        stepInto(INTELLIJ_FILTER),
        checkLine(SOURCE_FILE, 39),
        checkLocals("a", "b"),
        run());
  }

  @Test
  public void testLambda_StaticMethodReference_Trivial() throws Throwable {
    String initialMethodName = "printInt2";
    runDebugTest(
        getConfig(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, initialMethodName),
        run(),
        checkMethod(DEBUGGEE_CLASS, initialMethodName),
        checkLine(SOURCE_FILE, 22),
        stepInto(INTELLIJ_FILTER),
        checkMethod(DEBUGGEE_CLASS, "returnOne"),
        checkLine(SOURCE_FILE, 30),
        checkNoLocal(),
        run());
  }

  @Test
  public void testLambda_StaticMethodReference_NonTrivial() throws Throwable {
    String initialMethodName = "testLambdaWithMethodReferenceAndConversion";
    runDebugTest(
        getConfig(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, initialMethodName),
        run(),
        checkMethod(DEBUGGEE_CLASS, initialMethodName),
        checkLine(SOURCE_FILE, 48),
        stepInto(INTELLIJ_FILTER),
        inspect(t -> Assert.assertTrue(t.getMethodName().startsWith("lambda$"))),
        stepInto(INTELLIJ_FILTER),
        checkMethod(DEBUGGEE_CLASS, "concatObjects"),
        checkLine(SOURCE_FILE, 59),
        checkLocal("objects"),
        run());
  }

  private static void doNothing(JUnit3Wrapper jUnit3Wrapper) {
  }
}
