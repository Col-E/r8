// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.instanceofremoval;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UninstantiatedProgramTypeTest extends TestBase {

  static class A {}

  static class B extends A {}

  static class TestClass {

    public static void main(String[] args) {
      A obj = getObject();

      // Since B is a program class and B is never instantiated directly or indirectly, we can
      // rewrite the instance-of instruction to the constant false.
      if (obj instanceof B) {
        dead();
      } else {
        live();
      }
    }

    @NeverInline
    private static A getObject() {
      return new A();
    }

    @NeverInline
    private static void live() {
      System.out.print("In TestClass.live()");
    }

    @NeverInline
    private static void dead() {
      System.out.print("In TestClass.dead()");
    }
  }

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  private final Backend backend;

  public UninstantiatedProgramTypeTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    String expected = "In TestClass.live()";

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expected);

    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(UninstantiatedProgramTypeTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expected)
            .inspector();

    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertThat(classSubject.method("void", "live"), isPresent());
    assertThat(classSubject.method("void", "dead"), not(isPresent()));

    long numberOfInstanceOfInstructions =
        Streams.stream(
                classSubject.mainMethod().iterateInstructions(InstructionSubject::isInstanceOf))
            .count();
    assertEquals(0, numberOfInstanceOfInstructions);
  }
}
