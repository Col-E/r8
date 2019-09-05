// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites.dynamictype;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeVirtualPositiveTest extends TestBase {
  private static final Class<?> MAIN = Main.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public InvokeVirtualPositiveTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeVirtualPositiveTest.class)
        .addKeepMainRule(MAIN)
        .enableMergeAnnotations()
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("A:Sub1", "B:Sub1")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject a = inspector.clazz(A.class);
    assertThat(a, isPresent());

    MethodSubject a_m = a.uniqueMethodWithName("m");
    assertThat(a_m, isPresent());
    // TODO(b/139246447): Can optimize branches since the type of `arg` is Sub1.
    assertTrue(a_m.streamInstructions().anyMatch(InstructionSubject::isIf));

    ClassSubject b = inspector.clazz(B.class);
    assertThat(b, isPresent());

    MethodSubject b_m = b.uniqueMethodWithName("m");
    assertThat(b_m, isPresent());
    // TODO(b/139246447): Can optimize branches since the type of `arg` is Sub1.
    assertTrue(b_m.streamInstructions().anyMatch(InstructionSubject::isIf));
  }

  @NeverMerge
  static class Base {}
  static class Sub1 extends Base {}
  static class Sub2 extends Base {}

  @NeverMerge
  @NeverClassInline
  static class A {
    @NeverInline
    void m(Base arg) {
      if (arg instanceof Sub1) {
        System.out.println("A:Sub1");
      } else if (arg instanceof Sub2) {
        System.out.println("A:Sub2");
      }
    }
  }

  @NeverClassInline
  static class B extends A {
    @NeverInline
    @Override
    void m(Base arg) {
      if (arg instanceof Sub1) {
        System.out.println("B:Sub1");
      } else if (arg instanceof Sub2) {
        System.out.println("B:Sub2");
      }
    }
  }

  static class Main {
    public static void main(String... args) {
      Sub1 s1 = new Sub1();

      A a = System.currentTimeMillis() > 0 ? new A() : new B();
      a.m(s1); // calls A.m() with Sub1.

      B b = new B();
      b.m(s1); // calls B.m() with Sub1.
    }
  }
}
