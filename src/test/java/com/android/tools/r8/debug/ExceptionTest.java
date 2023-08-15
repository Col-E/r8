// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.classes.Exceptions;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests debugging behavior with regards to exception handling */
@RunWith(Parameterized.class)
public class ExceptionTest extends DebugTestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimesAndApiLevels()
        .withApiLevel(AndroidApiLevel.B)
        .build();
  }

  private static final String SOURCE_FILE = "Exceptions.java";
  private final String className = typeName(Exceptions.class);

  @Test
  public void testStepOnCatchCf() throws Throwable {
    assumeTrue(parameters.isCfRuntime());
    // Java jumps to first instruction of the catch handler, matching the source code.
    runDebugTest(
        testForJvm(parameters)
            .addProgramClasses(Exceptions.class)
            .debugConfig(parameters.getRuntime()),
        className,
        breakpoint(className, "catchException"),
        run(),
        checkLine(SOURCE_FILE, 11), // line of the method call throwing the exception
        stepOver(),
        checkLine(SOURCE_FILE, 13), // first line in the catch handler
        checkLocal("e"),
        run());
  }

  @Test
  public void testStepOnCatchD8() throws Throwable {
    assumeTrue(parameters.isDexRuntime());
    // ART/Dalvik jumps to 'move-exception' which initializes the local variable with the pending
    // exception. Thus it is "attached" to the line declaring the exception in the catch handler.
    runDebugTest(
        testForD8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramClasses(Exceptions.class)
            .compile()
            .debugConfig(parameters.getRuntime()),
        className,
        breakpoint(className, "catchException"),
        run(),
        checkLine(SOURCE_FILE, 11), // line of the method call throwing the exception
        stepOver(),
        checkLine(SOURCE_FILE, 12), // line of the catch declaration
        checkNoLocal("e"),
        stepOver(),
        checkLine(SOURCE_FILE, 13), // first line in the catch handler
        checkLocal("e"),
        run());
  }
}
