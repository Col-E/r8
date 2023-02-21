// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PruneNotNullBranchD8Test extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED = "foo_bar_baz0";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public PruneNotNullBranchD8Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject main = inspector.clazz(Main.class);
              assertThat(main, isPresent());
              MethodSubject mainMethod = main.mainMethod();
              assertThat(mainMethod, isPresent());
              if (parameters.isDexRuntime()) {
                assertFalse(mainMethod.streamInstructions().anyMatch(InstructionSubject::isThrow));
              } else {
                assert parameters.isCfRuntime();
                // We are not going through IR when running in CF
                assertTrue(mainMethod.streamInstructions().anyMatch(InstructionSubject::isThrow));
              }
            });
  }

  public static class Main {

    public static void main(String[] args) {
      File file;
      if (args.length == 0) {
        file = new File("foo_bar_baz0");
      } else {
        file = new File("foo_bar_baz1");
      }
      if (file != null) {
        System.out.println(file.getPath());
        return;
      }
      throw new RuntimeException("Will always be non-zero");
    }
  }
}
