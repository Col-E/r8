// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;

public class NoClassesOrMembersWithAnnotationsTest extends HorizontalClassMergingTestBase {
  public NoClassesOrMembersWithAnnotationsTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "a", "b", "c", "foo", "null", "annotation 2", "annotation 1", "annotation 2")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(TypeAnnotation.class), isPresent());
              assertThat(codeInspector.clazz(MethodAnnotation.class), isPresent());
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());
              assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface TypeAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  public @interface MethodAnnotation {}

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("a");
    }
  }

  @TypeAnnotation
  @NeverClassInline
  public static class B {
    public B(String v) {
      System.out.println(v);
    }
  }

  @NeverClassInline
  public static class C {
    public C(String v) {
      System.out.println(v);
    }

    @NeverInline
    @MethodAnnotation
    public void foo() {
      System.out.println("foo");
    }
  }

  static class Main {
    @NeverInline
    public static void foo(TypeAnnotation annotation) {
      System.out.println(annotation);
    }

    @NeverInline
    public static void foo2(MethodAnnotation annotation) {
      System.out.println(annotation.toString().replaceFirst(".*@.*", "annotation 2"));
    }

    public static void main(String[] args) throws NoSuchMethodException {
      A a = new A();
      B b = new B("b");
      C c = new C("c");
      c.foo();
      foo(null);
      foo2(
          new MethodAnnotation() {
            @Override
            public Class<? extends Annotation> annotationType() {
              return null;
            }
          });
      System.out.println(
          b.getClass().getAnnotations()[0].toString().replaceFirst(".*", "annotation 1"));
      System.out.println(
          c.getClass()
              .getMethods()[0]
              .getAnnotations()[0]
              .toString()
              .replaceFirst(".*", "annotation 2"));
    }
  }
}
