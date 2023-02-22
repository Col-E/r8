// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.applymapping.shared.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingReservationTest extends TestBase {

  // This is the program which we will not rename and will cause a naming conflict per b/133646663.
  public static class Runner {

    public static void main(String[] args) {
      System.out.print(new R().identifier);
    }
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testReservedNames() throws Exception {
    R8TestCompileResult libraryCompileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(R.class)
            .addKeepClassAndMembersRules(R.class)
            .setMinApi(parameters)
            .compile();
    testForR8(parameters.getBackend())
        .addProgramClasses(Runner.class)
        .addClasspathClasses(R.class)
        .addKeepMainRule(Runner.class)
        .addApplyMapping(R.class.getTypeName() + " -> " + R.class.getTypeName() + ":")
        .setMinApi(parameters)
        .noTreeShaking()
        .compile()
        .addRunClasspathFiles(libraryCompileResult.writeToZip())
        .run(parameters.getRuntime(), Runner.class)
        .assertSuccessWithOutput("0");
  }
}
