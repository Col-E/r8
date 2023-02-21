// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import static com.android.tools.r8.utils.codeinspector.AnnotationMatchers.hasParameterAnnotationTypes;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.MethodMatchers.hasParameters;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.TypeSubject;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoNormalizationWithParameterAnnotationsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Foo.class, Bar.class)
        .addKeepRuntimeVisibleAnnotations()
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with proto changes.
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              TypeSubject aTypeSubject = inspector.clazz(A.class).asTypeSubject();
              TypeSubject bTypeSubject = inspector.clazz(B.class).asTypeSubject();
              TypeSubject fooTypeSubject = inspector.clazz(Foo.class).asTypeSubject();
              TypeSubject barTypeSubject = inspector.clazz(Bar.class).asTypeSubject();

              // Main.bar() has parameter annotations [@Bar, @Foo].
              MethodSubject barMethodSubject = mainClassSubject.uniqueMethodWithOriginalName("bar");
              assertThat(barMethodSubject, isPresent());
              assertThat(barMethodSubject, hasParameters(aTypeSubject, bTypeSubject));
              assertThat(
                  barMethodSubject,
                  hasParameterAnnotationTypes(
                      ImmutableList.of(barTypeSubject), ImmutableList.of(fooTypeSubject)));

              // Main.baz() has parameter annotations [, @Foo].
              MethodSubject bazMethodSubject = mainClassSubject.uniqueMethodWithOriginalName("baz");
              assertThat(bazMethodSubject, isPresent());
              assertThat(bazMethodSubject, hasParameters(aTypeSubject, bTypeSubject));
              assertThat(
                  bazMethodSubject,
                  hasParameterAnnotationTypes(
                      ImmutableList.of(), ImmutableList.of(fooTypeSubject)));

              // Main.qux() has parameter annotations [@Foo, ].
              MethodSubject quxMethodSubject = mainClassSubject.uniqueMethodWithOriginalName("qux");
              assertThat(quxMethodSubject, isPresent());
              assertThat(quxMethodSubject, hasParameters(aTypeSubject, bTypeSubject));
              assertThat(
                  quxMethodSubject,
                  hasParameterAnnotationTypes(
                      ImmutableList.of(fooTypeSubject), ImmutableList.of()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "A", "B", "A", "B", "A", "B");
  }

  static class Main {

    public static void main(String[] args) {
      foo(new A(), new B());
      bar(new B(), new A());
      baz(new B(), new A());
      qux(new B(), new A());
    }

    @NeverInline
    static void foo(A a, B b) {
      System.out.println(a);
      System.out.println(b);
    }

    @NeverInline
    static void bar(@Foo B b, @Bar A a) {
      System.out.println(a);
      System.out.println(b);
    }

    @NeverInline
    static void baz(@Foo B b, A a) {
      System.out.println(a);
      System.out.println(b);
    }

    @NeverInline
    static void qux(B b, @Foo A a) {
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

  @Retention(RetentionPolicy.RUNTIME)
  @interface Foo {}

  @Retention(RetentionPolicy.RUNTIME)
  @interface Bar {}
}
