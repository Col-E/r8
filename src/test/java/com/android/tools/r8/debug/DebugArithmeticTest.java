// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.classes.Arithmetic;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Examples of debug test features. */
@RunWith(Parameterized.class)
public class DebugArithmeticTest extends DebugTestBase {

  public static final String SOURCE_FILE = "Arithmetic.java";
  public static final String DEBUGGEE_CLASS = typeName(Arithmetic.class);

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public DebugTestConfig getConfig() throws Exception {
    return testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Arithmetic.class)
        .debugConfig(parameters.getRuntime());
  }

  /**
   * Simple test that runs the debuggee until it exits.
   */
  @Test
  public void testRun() throws Throwable {
    runDebugTest(getConfig(), DEBUGGEE_CLASS, run());
  }

  /**
   * Tests that we do suspend on breakpoint then continue.
   */
  @Test
  public void testBreakpoint_Hit() throws Throwable {
    runDebugTest(
        getConfig(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, "bitwiseInts"),
        run(),
        checkLine(SOURCE_FILE, 14),
        run());
  }

  /**
   * Tests that we can check local variables at a suspension point (breakpoint).
   */
  @Test
  public void testLocalsOnBreakpoint() throws Throwable {
    runDebugTest(
        getConfig(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, "bitwiseInts"),
        run(),
        checkLine(SOURCE_FILE, 14),
        checkLocal("x", Value.createInt(12345)),
        checkLocal("y", Value.createInt(54321)),
        run());
  }

  /**
   * Tests that we can check local variables at different suspension points (breakpoint then step).
   */
  @Test
  public void testLocalsOnBreakpointThenStep() throws Throwable {
    runDebugTest(
        getConfig(),
        DEBUGGEE_CLASS,
        breakpoint(DEBUGGEE_CLASS, "bitwiseInts"),
        run(),
        checkLine(SOURCE_FILE, 14),
        checkLocal("x", Value.createInt(12345)),
        checkLocal("y", Value.createInt(54321)),
        stepOver(),
        checkLocal("x", Value.createInt(12345)),
        checkLocal("y", Value.createInt(54321)),
        run());
  }
}
