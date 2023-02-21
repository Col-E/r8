// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites.nullability;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeStaticNegativeTest extends TestBase {
  private static final Class<?> MAIN = Main.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeStaticNegativeTest.class)
        .addKeepMainRule(MAIN)
        .enableInliningAnnotations()
        .addOptionsModification(
            o ->
                o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("null", "non-null")
        .inspect(this::inspect);
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    assert false : "Unexpected revisit: " + method.toSourceString();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());

    MethodSubject test = main.uniqueMethodWithOriginalName("test");
    assertThat(test, isPresent());
    // Should not optimize branches since the nullability of `arg` is unsure.
    assertTrue(test.streamInstructions().anyMatch(InstructionSubject::isIf));
  }

  static class Main {
    public static void main(String... args) {
      test(null);         // calls test with null.
      test(new Object()); // calls test with non-null instance.
    }

    @NeverInline
    static void test(Object arg) {
      if (arg != null) {
        System.out.println("non-null");
      } else {
        System.out.println("null");
      }
    }
  }
}
