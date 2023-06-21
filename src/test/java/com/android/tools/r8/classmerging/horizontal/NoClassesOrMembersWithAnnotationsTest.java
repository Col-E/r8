// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.BooleanUtils;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.Test;
import org.junit.runners.Parameterized;

public class NoClassesOrMembersWithAnnotationsTest extends HorizontalClassMergingTestBase {

  private final boolean enableProguardCompatibilityMode;

  @Parameterized.Parameters(name = "{1}, compat: {0}")
  public static List<Object[]> parameters() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public NoClassesOrMembersWithAnnotationsTest(
      boolean enableProguardCompatibilityMode, TestParameters parameters) {
    super(parameters);
    this.enableProguardCompatibilityMode = enableProguardCompatibilityMode;
  }

  @Test
  public void testR8() throws Exception {
    testForR8Compat(parameters.getBackend(), enableProguardCompatibilityMode)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (enableProguardCompatibilityMode) {
                inspector.assertIsCompleteMergeGroup(A.class, C.class);
              } else {
                inspector.assertIsCompleteMergeGroup(A.class, B.class, C.class);
              }
              inspector.assertNoOtherClassesMerged();
            })
        .enableConstantArgumentAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(TypeAnnotation.class), isPresent());
              assertThat(codeInspector.clazz(MethodAnnotation.class), isPresent());
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(
                  codeInspector.clazz(B.class),
                  onlyIf(enableProguardCompatibilityMode, isPresent()));
              assertThat(codeInspector.clazz(C.class), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            enableProguardCompatibilityMode,
            result ->
                result.assertSuccessWithOutputLines(
                    "a", "b", "c", "foo", "null", "annotation 2", "annotation 1", "annotation 2"),
            result ->
                result.assertSuccessWithOutputLines("a", "b", "c", "foo", "null", "annotation 2"));
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface TypeAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  public @interface MethodAnnotation {}

  @TypeAnnotation
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
    @KeepConstantArguments
    @NeverInline
    public static void foo(TypeAnnotation annotation) {
      System.out.println(annotation);
    }

    @NeverInline
    public static void foo2(MethodAnnotation annotation) {
      System.out.println(annotation.toString().replaceFirst(".*@.*", "annotation 2"));
    }

    public static void main(String[] args) {
      A a = new A();
      B b = new B("b");
      C c = new C("c");
      c.foo();
      foo(null);
      @NoAccessModification
      class MethodAnnotationImpl implements MethodAnnotation {
        @Override
        public Class<? extends Annotation> annotationType() {
          return null;
        }
      }
      foo2(new MethodAnnotationImpl());
      if (b.getClass().getAnnotations().length > 0) {
        System.out.println(
            b.getClass().getAnnotations()[0].toString().replaceFirst(".*", "annotation 1"));
      }
      if (c.getClass().getMethods()[0].getAnnotations().length > 0) {
        System.out.println(
            c.getClass()
                .getMethods()[0]
                .getAnnotations()[0]
                .toString()
                .replaceFirst(".*", "annotation 2"));
      }
    }
  }
}
