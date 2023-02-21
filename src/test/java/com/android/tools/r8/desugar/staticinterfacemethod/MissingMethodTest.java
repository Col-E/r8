// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.staticinterfacemethod;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MissingMethodTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public MissingMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(InterfaceDump.dump(), MainDump.dump())
        .run(parameters.getRuntime(), "Main")
        .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(InterfaceDump.dump(), MainDump.dump())
        .addKeepMainRule("Main")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), "Main")
        .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
  }
}
