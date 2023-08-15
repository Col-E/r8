// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.classes.SharedCode;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SharedCodeTest extends DebugTestBase {

  public static final String CLASS = typeName(SharedCode.class);
  public static final String FILE = "SharedCode.java";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Test
  public void testSharedIf() throws Throwable {
    final String methodName = "sharedIf";
    runDebugTest(
        testForD8(parameters.getBackend())
            .addProgramClasses(SharedCode.class)
            .setMinApi(parameters)
            .debugConfig(parameters.getRuntime()),
        CLASS,
        breakpoint(CLASS, methodName),
        run(),
        checkMethod(CLASS, methodName),
        checkLine(FILE, 10),
        stepOver(),
        checkLine(FILE, 11),
        stepOver(),
        checkLine(FILE, 15),
        run(),
        checkMethod(CLASS, methodName),
        checkLine(FILE, 10),
        stepOver(),
        checkLine(FILE, 13),
        stepOver(),
        checkLine(FILE, 15),
        run());
  }

}
