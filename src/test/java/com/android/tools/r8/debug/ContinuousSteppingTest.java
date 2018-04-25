// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import java.util.Map;
import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ContinuousSteppingTest extends DebugTestBase {

  private static DebugTestConfig javaD8Config;
  private static DebugTestConfig kotlinD8Config;

  @BeforeClass
  public static void setup() {
    javaD8Config = new D8DebugTestResourcesConfig(temp);
    kotlinD8Config = new KotlinD8Config(temp);
  }

  @Test
  public void testArithmetic() throws Throwable {
    runContinuousTest("Arithmetic", javaD8Config);
  }

  @Test
  public void testLocals() throws Throwable {
    runContinuousTest("Locals", javaD8Config);
  }

  @Test
  public void testKotlinInline() throws Throwable {
    runContinuousTest("KotlinInline", kotlinD8Config);
  }

  private void runContinuousTest(String debuggeeClassName, DebugTestConfig config)
      throws Throwable {
    runDebugTest(
        config,
        debuggeeClassName,
        breakpoint(debuggeeClassName, "main"),
        run(),
        stepUntil(StepKind.OVER, StepLevel.INSTRUCTION, debuggeeState -> {
          // Fetch local variables.
          Map<String, Value> localValues = debuggeeState.getLocalValues();
          Assert.assertNotNull(localValues);

          // Always step until we actually exit the program.
          return false;
        }));
  }

}
