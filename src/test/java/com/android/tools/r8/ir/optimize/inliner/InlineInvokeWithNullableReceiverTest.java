// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Test for b/130202534. */
@RunWith(Parameterized.class)
public class InlineInvokeWithNullableReceiverTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InlineInvokeWithNullableReceiverTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult result =
        testForR8(parameters.getBackend())
            .addInnerClasses(InlineInvokeWithNullableReceiverTest.class)
            .addKeepMainRule(TestClass.class)
            .setMinApi(parameters)
            .compile()
            .inspect(this::verifyMethodHasBeenInlined);

    result
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.lines("Hello world!"));

    // Invoking main() with a non-zero argument list should cause the program to throw.
    result
        .run(parameters.getRuntime(), TestClass.class, "42")
        .assertFailureWithErrorThatMatches(containsString("NullPointerException"));
  }

  private void verifyMethodHasBeenInlined(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    MethodSubject methodSubject = classSubject.mainMethod();
    assertThat(methodSubject, isPresent());

    // A `throw` instruction should have been synthesized into main().
    assertTrue(
        methodSubject
            .streamInstructions()
            .filter(InstructionSubject::isInvokeVirtual)
            .anyMatch(
                method ->
                    method
                        .getMethod()
                        .toSourceString()
                        .equals("java.lang.Class java.lang.Object.getClass()")));

    // Class A is still present because the instance flows into a phi that has a null-check.
    ClassSubject otherClassSubject = inspector.clazz(A.class);
    assertThat(otherClassSubject, isPresent());

    // Method A.m() should no longer be present due to inlining.
    assertThat(otherClassSubject.uniqueMethodWithOriginalName("m"), not(isPresent()));
  }

  private boolean canUseRequireNonNull() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  static class TestClass {

    public static void main(String[] args) {
      A obj = args.length == 0 ? new A() : null;
      obj.m();
    }
  }

  static class A {

    public void m() {
      System.out.println("Hello world!");
    }
  }
}
