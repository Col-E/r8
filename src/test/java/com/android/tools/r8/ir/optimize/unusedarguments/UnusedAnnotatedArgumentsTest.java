// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepUnusedArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedAnnotatedArgumentsTest extends TestBase {

  private final boolean keepUnusedArguments;
  private final TestParameters parameters;

  @Parameters(name = "{1}, keep unused arguments: {0}")
  public static List<Object[]> params() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public UnusedAnnotatedArgumentsTest(boolean keepUnusedArguments, TestParameters parameters) {
    this.keepUnusedArguments = keepUnusedArguments;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(UnusedAnnotatedArgumentsTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(Used.class, Unused.class)
        .addKeepAttributes("RuntimeVisibleParameterAnnotations")
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableUnusedArgumentAnnotations(keepUnusedArguments)
        // TODO(b/123060011): Mapping not working in presence of unused argument removal.
        .minification(keepUnusedArguments)
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::verifyOutput)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.times(StringUtils.lines("Hello world!"), 6));
  }

  private void verifyOutput(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    ClassSubject usedClassSubject = inspector.clazz(Used.class);
    assertThat(usedClassSubject, isPresent());

    ClassSubject unusedClassSubject = inspector.clazz(Unused.class);
    assertThat(unusedClassSubject, isPresent());

    List<MethodSubject> methodSubjects =
        ImmutableList.of(
            testClassSubject.uniqueMethodWithName("testRemoveStaticFromStart"),
            testClassSubject.uniqueMethodWithName("testRemoveStaticFromMiddle"),
            testClassSubject.uniqueMethodWithName("testRemoveStaticFromEnd"),
            testClassSubject.uniqueMethodWithName("testRemoveVirtualFromStart"),
            testClassSubject.uniqueMethodWithName("testRemoveVirtualFromMiddle"),
            testClassSubject.uniqueMethodWithName("testRemoveVirtualFromEnd"));

    for (MethodSubject methodSubject : methodSubjects) {
      assertThat(methodSubject, isPresent());

      if (keepUnusedArguments) {
        assertEquals(3, methodSubject.getMethod().method.proto.parameters.size());
        assertEquals(3, methodSubject.getMethod().parameterAnnotationsList.size());

        for (int i = 0; i < 3; ++i) {
          DexAnnotationSet annotationSet =
              methodSubject.getMethod().parameterAnnotationsList.get(i);
          assertEquals(1, annotationSet.annotations.length);

          DexAnnotation annotation = annotationSet.annotations[0];
          if (i == getPositionOfUnusedArgument(methodSubject)) {
            assertEquals(
                unusedClassSubject.getFinalName(), annotation.annotation.type.toSourceString());
          } else {
            assertEquals(
                usedClassSubject.getFinalName(), annotation.annotation.type.toSourceString());
          }
        }
      } else {
        assertEquals(2, methodSubject.getMethod().method.proto.parameters.size());
        assertEquals(2, methodSubject.getMethod().parameterAnnotationsList.size());

        for (int i = 0; i < 2; ++i) {
          DexAnnotationSet annotationSet =
              methodSubject.getMethod().parameterAnnotationsList.get(i);
          assertEquals(1, annotationSet.annotations.length);

          DexAnnotation annotation = annotationSet.annotations[0];
          assertEquals(
              usedClassSubject.getFinalName(), annotation.annotation.type.toSourceString());
        }
      }
    }
  }

  private static int getPositionOfUnusedArgument(MethodSubject methodSubject) {
    switch (methodSubject.getOriginalName(false)) {
      case "testRemoveStaticFromStart":
      case "testRemoveVirtualFromStart":
        return 0;

      case "testRemoveStaticFromMiddle":
      case "testRemoveVirtualFromMiddle":
        return 1;

      case "testRemoveStaticFromEnd":
      case "testRemoveVirtualFromEnd":
        return 2;

      default:
        throw new Unreachable();
    }
  }

  @NeverClassInline
  static class TestClass {

    public static void main(String[] args) {
      testRemoveStaticFromStart(null, "Hello", " world!");
      testRemoveStaticFromMiddle("Hello", null, " world!");
      testRemoveStaticFromEnd("Hello", " world!", null);
      new TestClass().testRemoveVirtualFromStart(null, "Hello", " world!");
      new TestClass().testRemoveVirtualFromMiddle("Hello", null, " world!");
      new TestClass().testRemoveVirtualFromEnd("Hello", " world!", null);
    }

    @KeepUnusedArguments
    @NeverInline
    static void testRemoveStaticFromStart(
        @Unused String unused, @Used String used, @Used String otherUsed) {
      System.out.println(used + otherUsed);
    }

    @KeepUnusedArguments
    @NeverInline
    static void testRemoveStaticFromMiddle(
        @Used String used, @Unused String unused, @Used String otherUsed) {
      System.out.println(used + otherUsed);
    }

    @KeepUnusedArguments
    @NeverInline
    static void testRemoveStaticFromEnd(
        @Used String used, @Used String otherUsed, @Unused String unused) {
      System.out.println(used + otherUsed);
    }

    @KeepUnusedArguments
    @NeverInline
    void testRemoveVirtualFromStart(
        @Unused String unused, @Used String used, @Used String otherUsed) {
      System.out.println(used + otherUsed);
    }

    @KeepUnusedArguments
    @NeverInline
    void testRemoveVirtualFromMiddle(
        @Used String used, @Unused String unused, @Used String otherUsed) {
      System.out.println(used + otherUsed);
    }

    @KeepUnusedArguments
    @NeverInline
    void testRemoveVirtualFromEnd(
        @Used String used, @Used String otherUsed, @Unused String unused) {
      System.out.println(used + otherUsed);
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface Used {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface Unused {}
}
