// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import java.nio.file.Path;
import org.junit.Test;

/**
 * Tests debugging behavior with regards to exception handling
 */
public class ExceptionTest extends DebugTestBase {

  private static final Path JAR = DebugTestBase.DEBUGGEE_JAR;
  private static final String SOURCE_FILE = "Exceptions.java";

  @Test
  public void testStepOnCatchCf() throws Throwable {
    // Java jumps to first instruction of the catch handler, matching the source code.
    runDebugTest(
        new CfDebugTestConfig(JAR),
        "Exceptions",
        breakpoint("Exceptions", "catchException"),
        run(),
        checkLine(SOURCE_FILE, 9), // line of the method call throwing the exception
        stepOver(),
        checkLine(SOURCE_FILE, 11), // first line in the catch handler
        checkLocal("e"),
        run());
  }

  @Test
  public void testStepOnCatchD8() throws Throwable {
    // ART/Dalvik jumps to 'move-exception' which initializes the local variable with the pending
    // exception. Thus it is "attached" to the line declaring the exception in the catch handler.
    runDebugTest(
        new D8DebugTestResourcesConfig(temp),
        "Exceptions",
        breakpoint("Exceptions", "catchException"),
        run(),
        checkLine(SOURCE_FILE, 9), // line of the method call throwing the exception
        stepOver(),
        checkLine(SOURCE_FILE, 10), // line of the catch declaration
        checkNoLocal("e"),
        stepOver(),
        checkLine(SOURCE_FILE, 11), // first line in the catch handler
        checkLocal("e"),
        run());
  }
}
