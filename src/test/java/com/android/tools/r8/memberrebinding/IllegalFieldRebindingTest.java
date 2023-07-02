// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.memberrebinding.testclasses.IllegalFieldRebindingTestClasses;
import com.android.tools.r8.memberrebinding.testclasses.IllegalFieldRebindingTestClasses.B;
import com.android.tools.r8.utils.StringUtils;
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
public class IllegalFieldRebindingTest extends TestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("42");
  private static final String OTHER_EXPECTED_OUTPUT = StringUtils.lines("0");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(IllegalFieldRebindingTest.class)
            .addInnerClasses(IllegalFieldRebindingTestClasses.class)
            .addKeepMainRule(TestClass.class)
            .enableNoAccessModificationAnnotationsForClasses()
            .enableNoVerticalClassMergingAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();

    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());

    MethodSubject methodSubject = classSubject.mainMethod();
    assertThat(methodSubject, isPresent());

    // Verify that the static-put instruction has not been removed.
    assertEquals(
        1, methodSubject.streamInstructions().filter(InstructionSubject::isStaticPut).count());
  }

  @Test
  public void testJvmOther() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), OtherTestClass.class)
        .assertSuccessWithOutput(OTHER_EXPECTED_OUTPUT);
  }

  @Test
  public void otherTest() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(IllegalFieldRebindingTest.class)
            .addInnerClasses(IllegalFieldRebindingTestClasses.class)
            .addKeepMainRule(OtherTestClass.class)
            .enableNoAccessModificationAnnotationsForClasses()
            .enableNoVerticalClassMergingAnnotations()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), OtherTestClass.class)
            .assertSuccessWithOutput(OTHER_EXPECTED_OUTPUT)
            .inspector();

    // The static-get instruction in OtherTestClass.main() should have been replaced by the
    // constant 0, which makes the remaining classes unreachable.
    assertEquals(1, inspector.allClasses().size());

    ClassSubject classSubject = inspector.clazz(OtherTestClass.class);
    assertThat(classSubject, isPresent());

    MethodSubject methodSubject = classSubject.mainMethod();
    assertThat(methodSubject, isPresent());

    // Verify that a constant 0 is being loaded in main().
    assertEquals(
        1,
        methodSubject
            .streamInstructions()
            .filter(instruction -> instruction.isConstNumber(0))
            .count());
  }

  static class TestClass {

    public static void main(String[] args) {
      // Cannot be member rebound because A is inaccessible to this package. However, the
      // instruction cannot be removed as the field `A.f` is actually read.
      B.f = 42;
      B.print();
    }
  }

  static class OtherTestClass {

    public static void main(String[] args) {
      // Cannot be member rebound because A is inaccessible to this package.
      // However, the instruction should still be replaced by the constant 0.
      System.out.println(B.f);
    }
  }
}
