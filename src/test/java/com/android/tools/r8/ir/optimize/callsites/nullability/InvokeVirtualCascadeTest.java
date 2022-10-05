// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites.nullability;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeVirtualCascadeTest extends TestBase {
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
        .addInnerClasses(InvokeVirtualCascadeTest.class)
        .addKeepMainRule(MAIN)
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("A", "A", "B", "B")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject a = inspector.clazz(A.class);
    assertThat(a, isPresent());

    MethodSubject a_m = a.uniqueMethodWithOriginalName("m");
    assertThat(a_m, isPresent());
      assertTrue(a_m.streamInstructions().noneMatch(InstructionSubject::isIf));

    ClassSubject b = inspector.clazz(B.class);
    assertThat(b, isPresent());

    MethodSubject b_m = b.uniqueMethodWithOriginalName("m");
    assertThat(b_m, isPresent());
    assertTrue(b_m.streamInstructions().noneMatch(InstructionSubject::isIf));
  }

  @NoVerticalClassMerging
  @NeverClassInline
  static class A {
    @NeverInline
    void m(Object arg) {
      // Technically same as String#valueOf.
      if (arg != null) {
        System.out.println(arg.toString());
      } else {
        System.out.println("null");
      }
    }

    @NeverInline
    @Override
    public String toString() {
      return "A";
    }
  }

  @NeverClassInline
  static class B extends A {
    @NeverInline
    @Override
    void m(Object arg) {
      // Same as A#m.
      if (arg != null) {
        System.out.println(arg.toString());
      } else {
        System.out.println("null");
      }
    }

    @NeverInline
    @Override
    public String toString() {
      return "B";
    }
  }

  static class Main {
    public static void main(String... args) {
      A a = new A();
      test(a, a); // calls A.m() with non-null instance.
      a.m(a);  // calls A.m() with non-null instance.

      B b = new B();
      test(b, b); // calls B.m() with non-null instance.
      b.m(b);  // calls B.m() with non-null instance.
    }

    @NeverInline
    static void test(A rcv, A arg) {
      // `rcv` and `arg` are always not null, but until 2nd round of processing, we don't know yet.
      rcv.m(arg);
    }
  }
}
