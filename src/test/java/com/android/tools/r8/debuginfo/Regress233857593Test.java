// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Regress233857593Test extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            // TODO(b/233857593): There used to be only one goto.
            inspector -> {
              assertEquals(
                  2,
                  inspector
                      .clazz(TestClass.class)
                      .uniqueMethodWithName("testLoopPhiWithNullFirstInput")
                      .streamInstructions()
                      .filter(InstructionSubject::isGoto)
                      .count());
            });
  }

  static class TestClass {
    private void testLoopPhiWithNullFirstInput(boolean cond) {
      TestClass a = null;
      while (a == null) {
        if (cond) {
          a = new TestClass();
        }
      }
    }
  }
}
