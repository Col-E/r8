// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.KeepConstantArguments;
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
public class UninstantiatedAnnotatedArgumentsTest extends TestBase {

  private final boolean keepUninstantiatedArguments;
  private final TestParameters parameters;

  @Parameters(name = "{1}, keep uninstantiated arguments: {0}")
  public static List<Object[]> params() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public UninstantiatedAnnotatedArgumentsTest(
      boolean keepUninstantiatedArguments, TestParameters parameters) {
    this.keepUninstantiatedArguments = keepUninstantiatedArguments;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(UninstantiatedAnnotatedArgumentsTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(Instantiated.class, Uninstantiated.class)
        .addKeepAttributes("RuntimeVisibleParameterAnnotations")
        .enableNeverClassInliningAnnotations()
        .enableConstantArgumentAnnotations(keepUninstantiatedArguments)
        .enableInliningAnnotations()
        .enableUnusedArgumentAnnotations()
        // TODO(b/123060011): Mapping not working in presence of argument removal.
        .minification(keepUninstantiatedArguments)
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::verifyOutput)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.times(StringUtils.lines("Hello world!"), 6));
  }

  private void verifyOutput(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    ClassSubject instantiatedClassSubject = inspector.clazz(Instantiated.class);
    assertThat(instantiatedClassSubject, isPresent());

    ClassSubject uninstantiatedClassSubject = inspector.clazz(Uninstantiated.class);
    assertThat(uninstantiatedClassSubject, isPresent());

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

      // TODO(b/131735725): Should also remove arguments from the virtual methods.
      if (keepUninstantiatedArguments || methodSubject.getOriginalName().contains("Virtual")) {
        assertEquals(3, methodSubject.getMethod().method.proto.parameters.size());
        assertEquals(3, methodSubject.getMethod().parameterAnnotationsList.size());

        for (int i = 0; i < 3; ++i) {
          DexAnnotationSet annotationSet =
              methodSubject.getMethod().parameterAnnotationsList.get(i);
          assertEquals(1, annotationSet.annotations.length);

          DexAnnotation annotation = annotationSet.annotations[0];
          if (i == getPositionOfUnusedArgument(methodSubject)) {
            assertEquals(
                uninstantiatedClassSubject.getFinalName(),
                annotation.annotation.type.toSourceString());
          } else {
            assertEquals(
                instantiatedClassSubject.getFinalName(),
                annotation.annotation.type.toSourceString());
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
              instantiatedClassSubject.getFinalName(), annotation.annotation.type.toSourceString());
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

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    static void testRemoveStaticFromStart(
        @Uninstantiated Dead uninstantiated,
        @Instantiated String instantiated,
        @Instantiated String otherInstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    static void testRemoveStaticFromMiddle(
        @Instantiated String instantiated,
        @Uninstantiated Dead uninstantiated,
        @Instantiated String otherInstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    static void testRemoveStaticFromEnd(
        @Instantiated String instantiated,
        @Instantiated String otherInstantiated,
        @Uninstantiated Dead uninstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    void testRemoveVirtualFromStart(
        @Uninstantiated Dead uninstantiated,
        @Instantiated String instantiated,
        @Instantiated String otherInstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    void testRemoveVirtualFromMiddle(
        @Instantiated String instantiated,
        @Uninstantiated Dead uninstantiated,
        @Instantiated String otherInstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    void testRemoveVirtualFromEnd(
        @Instantiated String instantiated,
        @Instantiated String otherInstantiated,
        @Uninstantiated Dead uninstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }
  }

  static class Dead {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface Instantiated {}

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.PARAMETER)
  @interface Uninstantiated {}
}
