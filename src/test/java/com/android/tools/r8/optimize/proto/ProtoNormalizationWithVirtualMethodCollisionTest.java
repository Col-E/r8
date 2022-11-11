// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.MethodMatchers.hasParameters;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoNormalizationWithVirtualMethodCollisionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final String[] EXPECTED =
      new String[] {"A::foo", "B", "A", "B::foo", "B", "A", "B::foo", "B", "A"};

  private final String[] R8_EXPECTED =
      new String[] {"A::foo", "B", "A", "A::foo", "B", "A", "B::foo", "B", "A"};

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addDontObfuscate()
        .addKeepClassAndMembersRules(B.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/258720808): We should not produce incorrect results.
        .assertSuccessWithOutputLines(R8_EXPECTED)
        .inspect(
            inspector -> {
              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());

              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              TypeSubject bTypeSubject = bClassSubject.asTypeSubject();
              TypeSubject aTypeSubject = aClassSubject.asTypeSubject();

              MethodSubject fooMethodSubject = aClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(fooMethodSubject, isPresent());
              assertThat(fooMethodSubject, hasParameters(aTypeSubject, bTypeSubject));

              // TODO(b/173398086): Consider rewriting B.foo(B, A) to B.foo(A, B, C) instead of
              //  B.foo$1(A, B).
              MethodSubject otherFooMethodSubject =
                  bClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(otherFooMethodSubject, isPresent());
              assertThat(otherFooMethodSubject, hasParameters(aTypeSubject, bTypeSubject));
            });
  }

  static class Main {

    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      a.foo(b, a);
      a.foo(a, b);
      b.foo(a, b);
    }
  }

  @NoVerticalClassMerging
  static class B {

    @NeverInline
    public void foo(A a, B b) {
      System.out.println("B::foo");
      System.out.println(a);
      System.out.println(b);
    }

    @Override
    public String toString() {
      return "A";
    }
  }

  static class A extends B {

    @NeverInline
    public void foo(B b, A a) {
      System.out.println("A::foo");
      System.out.println(a);
      System.out.println(b);
    }

    @Override
    public String toString() {
      return "B";
    }
  }
}
