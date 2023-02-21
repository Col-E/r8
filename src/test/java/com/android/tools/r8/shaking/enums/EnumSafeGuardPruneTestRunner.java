// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.enums;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumSafeGuardPruneTestRunner extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED = "SOUTH";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public EnumSafeGuardPruneTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(EnumSafeGuardPruneTestRunner.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumSafeGuardPruneTestRunner.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(Main.class);
    assertThat(main, isPresent());
    MethodSubject mainMethod = main.mainMethod();
    assertThat(mainMethod, isPresent());
    boolean hasRemoveThisBranch =
        mainMethod
            .streamInstructions()
            .anyMatch(instr -> instr.isConstString("Remove this branch", JumboStringMode.DISALLOW));
    // TODO(b/170084314): Consider removing this.
    assertTrue(hasRemoveThisBranch);
  }

  public enum Corners {
    NORTH,
    SOUTH,
    WEST
  }

  public static class Main {

    public static void main(String[] args) {
      Corners corners =
          args.length == 0 ? Corners.SOUTH : (args.length == 1 ? Corners.NORTH : Corners.WEST);
      switch (corners) {
        case NORTH:
          System.out.println("NORTH");
          return;
        case SOUTH:
          System.out.println("SOUTH");
          return;
        case WEST:
          System.out.println("WEST");
          return;
        default:
          throw new RuntimeException("Remove this branch");
      }
    }
  }
}
