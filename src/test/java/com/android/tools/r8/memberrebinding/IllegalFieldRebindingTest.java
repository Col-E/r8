// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
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
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IllegalFieldRebindingTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return ToolHelper.getBackends();
  }

  public IllegalFieldRebindingTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("42");

    if (backend == Backend.CF) {
      testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);
    }

    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(IllegalFieldRebindingTest.class)
            .addInnerClasses(IllegalFieldRebindingTestClasses.class)
            .addKeepMainRule(TestClass.class)
            .enableNoVerticalClassMergingAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
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
  public void otherTest() throws Exception {
    String expectedOutput = StringUtils.lines("0");

    if (backend == Backend.CF) {
      testForJvm()
          .addTestClasspath()
          .run(OtherTestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(IllegalFieldRebindingTest.class)
            .addInnerClasses(IllegalFieldRebindingTestClasses.class)
            .addKeepMainRule(OtherTestClass.class)
            .enableNoVerticalClassMergingAnnotations()
            .run(OtherTestClass.class)
            .assertSuccessWithOutput(expectedOutput)
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
