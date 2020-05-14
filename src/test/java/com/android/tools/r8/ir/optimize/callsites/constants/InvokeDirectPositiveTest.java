// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites.constants;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.utils.InternalOptions.CallSiteOptimizationOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeDirectPositiveTest extends TestBase {

  private static final Class<?> MAIN = Main.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public InvokeDirectPositiveTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeDirectPositiveTest.class)
        .addKeepMainRule(MAIN)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(
            o -> {
              o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect;
            })
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(CallSiteOptimizationOptions::enableConstantPropagationForTesting)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("non-null")
        .inspect(this::inspect);
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    assert method.getReference().name.toString().equals("test")
        : "Unexpected revisit: " + method.toSourceString();
    CallSiteOptimizationInfo callSiteOptimizationInfo =
        method.getDefinition().getCallSiteOptimizationInfo();
    AbstractValue abstractValue = callSiteOptimizationInfo.getAbstractArgumentValue(1);
    assert abstractValue.isSingleStringValue()
        && abstractValue.asSingleStringValue().getDexString().toString().equals("nul");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());
    MethodSubject test = main.uniqueMethodWithName("test");
    assertThat(test, isPresent());
    // Can optimize branches since `arg` is definitely "nul", i.e., not containing "null".
    assertTrue(test.streamInstructions().noneMatch(InstructionSubject::isIf));
  }

  @NeverClassInline
  static class Main {
    public static void main(String... args) {
      Main obj = new Main();
      obj.test("nul"); // calls test with "nul".
    }

    @NeverInline
    private void test(String arg) {
      if (arg.contains("null")) {
        System.out.println("null");
      } else {
        System.out.println("non-null");
      }
    }
  }
}
