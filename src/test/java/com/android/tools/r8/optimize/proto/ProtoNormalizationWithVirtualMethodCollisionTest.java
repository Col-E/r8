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

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with proto changes.
        .addDontObfuscate()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());

              TypeSubject aTypeSubject = aClassSubject.asTypeSubject();
              TypeSubject bTypeSubject = bClassSubject.asTypeSubject();

              MethodSubject fooMethodSubject = aClassSubject.uniqueMethodWithOriginalName("foo");
              assertThat(fooMethodSubject, isPresent());
              assertThat(fooMethodSubject, hasParameters(aTypeSubject, bTypeSubject));

              // TODO(b/173398086): Consider rewriting B.foo(B, A) to B.foo(A, B, C) instead of
              //  B.foo$1(A, B).
              MethodSubject otherFooMethodSubject =
                  bClassSubject.uniqueMethodWithOriginalName("foo$1");
              assertThat(otherFooMethodSubject, isPresent());
              assertThat(otherFooMethodSubject, hasParameters(aTypeSubject, bTypeSubject));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "A", "B");
  }

  static class Main {

    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      a.foo(a, b);
      b.foo(b, a);
    }
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    public void foo(A a, B b) {
      System.out.println(a);
      System.out.println(b);
    }

    @Override
    public String toString() {
      return "A";
    }
  }

  static class B extends A {

    @NeverInline
    public void foo(B b, A a) {
      System.out.println(a);
      System.out.println(b);
    }

    @Override
    public String toString() {
      return "B";
    }
  }
}
