// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.classes.MultipleReturns;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests debugging of method with multiple return statements. */
@RunWith(Parameterized.class)
public class MultipleReturnsTest extends DebugTestBase {

  public static final String SOURCE_FILE = "MultipleReturns.java";
  public static final String CLASS_NAME = typeName(MultipleReturns.class);

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Test
  public void testMultipleReturns() throws Throwable {
    runDebugTest(
        testForD8(parameters.getBackend())
            .setMinApi(parameters)
            .addProgramClasses(MultipleReturns.class)
            .debugConfig(parameters.getRuntime()),
        CLASS_NAME,
        breakpoint(CLASS_NAME, "multipleReturns"),
        run(),
        stepOver(),
        checkLine(SOURCE_FILE, 18), // this should be the 1st return statement
        run(),
        stepOver(),
        checkLine(SOURCE_FILE, 20), // this should be the 2nd return statement
        run());
  }
}
