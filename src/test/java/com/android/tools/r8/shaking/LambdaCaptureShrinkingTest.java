// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaCaptureShrinkingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.N)
        .build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .applyIf(
            parameters.isCfRuntime(),
            compileResult ->
                compileResult.inspect(
                    inspector -> {
                      MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
                      assertTrue(
                          mainMethodSubject
                              .streamInstructions()
                              .anyMatch(InstructionSubject::isInvokeDynamic));
                    }))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true");
  }

  static class Main {

    public static void main(String[] args) {
      PredicateInterface f = new Predicate();
      System.out.println(Arrays.asList(args).stream().noneMatch(f::m));
    }
  }

  @NoVerticalClassMerging
  interface PredicateInterfaceBase {
    boolean m(String item);
  }

  @NoVerticalClassMerging
  interface PredicateInterface extends PredicateInterfaceBase {}

  static class Predicate implements PredicateInterface {

    @Override
    public boolean m(String item) {
      return System.currentTimeMillis() > 0;
    }
  }
}
