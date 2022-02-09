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
        .addOptionsModification(
            options -> options.testing.enableExperimentalProtoNormalization = true)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
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

              MethodSubject fooMethodSubject = aClassSubject.uniqueMethodWithName("foo");
              assertThat(fooMethodSubject, isPresent());
              assertThat(fooMethodSubject, hasParameters(aTypeSubject, bTypeSubject));

              // TODO(b/173398086): Rewriting B.foo(B, A) to B.foo(A, B) would lead to B.foo()
              //  starting to override A.foo(A, B). B.foo(B, A) could either be rewritten to
              //  B.foo$1(A, B) if B.foo(B, A) is not related by overriding to a kept method, or an
              //  extra unused argument could be appended.
              MethodSubject otherFooMethodSubject = bClassSubject.uniqueMethodWithName("foo");
              assertThat(otherFooMethodSubject, isPresent());
              assertThat(otherFooMethodSubject, hasParameters(bTypeSubject, aTypeSubject));
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
