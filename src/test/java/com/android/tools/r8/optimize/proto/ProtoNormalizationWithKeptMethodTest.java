// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.MethodMatchers.hasParameters;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoNormalizationWithKeptMethodTest extends TestBase {

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
        .addKeepRules("-keep class " + Main.class.getTypeName() + " { void foo(...); }")
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with proto changes.
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              TypeSubject aTypeSubject = inspector.clazz(A.class).asTypeSubject();
              TypeSubject bTypeSubject = inspector.clazz(B.class).asTypeSubject();

              MethodSubject fooMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("foo");
              assertThat(fooMethodSubject, isPresent());
              assertThat(fooMethodSubject, hasParameters(bTypeSubject, aTypeSubject));

              for (String methodName : new String[] {"bar", "baz"}) {
                MethodSubject methodSubject =
                    inspector.clazz(Main.class).uniqueMethodWithOriginalName(methodName);
                assertThat(methodSubject, isPresent());
                assertThat(methodSubject, hasParameters(bTypeSubject, aTypeSubject));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "A", "B", "A", "B");
  }

  static class Main {

    public static void main(String[] args) {
      foo(new B(), new A());
      bar(new B(), new A());
      baz(new A(), new B());
    }

    // @Keep
    @NeverInline
    static void foo(B b, A a) {
      System.out.println(a);
      System.out.println(b);
    }

    @NeverInline
    static void bar(B b, A a) {
      System.out.println(a);
      System.out.println(b);
    }

    @NeverInline
    static void baz(A a, B b) {
      System.out.println(a);
      System.out.println(b);
    }
  }

  @NoHorizontalClassMerging
  static class A {

    @Override
    public String toString() {
      return "A";
    }
  }

  @NoHorizontalClassMerging
  static class B {

    @Override
    public String toString() {
      return "B";
    }
  }
}
