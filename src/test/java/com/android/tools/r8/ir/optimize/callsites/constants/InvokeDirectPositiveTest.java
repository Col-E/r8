// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites.constants;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeDirectPositiveTest extends TestBase {

  private static final Class<?> MAIN = Main.class;

  @Parameters(name = "{1}, experimental: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  private final boolean enableExperimentalArgumentPropagation;
  private final TestParameters parameters;

  public InvokeDirectPositiveTest(
      boolean enableExperimentalArgumentPropagation, TestParameters parameters) {
    this.enableExperimentalArgumentPropagation = enableExperimentalArgumentPropagation;
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeDirectPositiveTest.class)
        .addKeepMainRule(MAIN)
        .addOptionsModification(
            o -> {
              if (!enableExperimentalArgumentPropagation) {
                o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect;
              }
              o.callSiteOptimizationOptions()
                  .setEnableLegacyConstantPropagation()
                  .setEnableExperimentalArgumentPropagation(enableExperimentalArgumentPropagation);
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with argument removal.
        .minification(!enableExperimentalArgumentPropagation)
        .setMinApi(parameters.getApiLevel())
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

    if (enableExperimentalArgumentPropagation) {
      // Verify that the "nul" argument has been propagated to the test() method.
      MethodSubject mainMethodSubject = main.mainMethod();
      assertThat(mainMethodSubject, isPresent());
      assertTrue(
          mainMethodSubject.streamInstructions().noneMatch(InstructionSubject::isConstString));
    }

    MethodSubject test = main.uniqueMethodWithName("test");
    assertThat(test, isPresent());
    assertEquals(
        1 - BooleanUtils.intValue(enableExperimentalArgumentPropagation),
        test.getProgramMethod().getReference().getArity());
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
