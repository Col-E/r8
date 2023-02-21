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
import com.android.tools.r8.NoParameterReordering;
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

  private final boolean enableProguardCompatibilityMode;
  private final boolean keepUninstantiatedArguments;
  private final TestParameters parameters;

  @Parameters(name = "{2}, compat: {0}, keep uninstantiated arguments: {1}")
  public static List<Object[]> params() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public UninstantiatedAnnotatedArgumentsTest(
      boolean enableProguardCompatibilityMode,
      boolean keepUninstantiatedArguments,
      TestParameters parameters) {
    this.enableProguardCompatibilityMode = enableProguardCompatibilityMode;
    this.keepUninstantiatedArguments = keepUninstantiatedArguments;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend(), enableProguardCompatibilityMode)
        .addInnerClasses(UninstantiatedAnnotatedArgumentsTest.class)
        .addConstantArgumentAnnotations()
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(Instantiated.class, Uninstantiated.class)
        .addKeepRuntimeVisibleParameterAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableConstantArgumentAnnotations(keepUninstantiatedArguments)
        .enableInliningAnnotations()
        .enableNoParameterReorderingAnnotations()
        .enableUnusedArgumentAnnotations()
        // TODO(b/123060011): Mapping not working in presence of argument removal.
        .minification(keepUninstantiatedArguments)
        .setMinApi(parameters)
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
            testClassSubject.uniqueMethodWithOriginalName("testRemoveStaticFromStart"),
            testClassSubject.uniqueMethodWithOriginalName("testRemoveStaticFromMiddle"),
            testClassSubject.uniqueMethodWithOriginalName("testRemoveStaticFromEnd"),
            testClassSubject.uniqueMethodWithOriginalName("testRemoveVirtualFromStart"),
            testClassSubject.uniqueMethodWithOriginalName("testRemoveVirtualFromMiddle"),
            testClassSubject.uniqueMethodWithOriginalName("testRemoveVirtualFromEnd"));

    for (MethodSubject methodSubject : methodSubjects) {
      assertThat(methodSubject, isPresent());

      if (keepUninstantiatedArguments) {
        assertEquals(3, methodSubject.getMethod().getParameters().size());

        // In non-compat mode, R8 removes annotations from non-pinned items.
        assertEquals(
            enableProguardCompatibilityMode ? 3 : 0,
            methodSubject.getMethod().getParameterAnnotations().size());
      } else {
        assertEquals(2, methodSubject.getMethod().getReference().proto.parameters.size());
        assertEquals(
            enableProguardCompatibilityMode ? 2 : 0,
            methodSubject.getMethod().getParameterAnnotations().size());
      }

      for (int i = 0; i < methodSubject.getMethod().getParameterAnnotations().size(); ++i) {
        DexAnnotationSet annotationSet = methodSubject.getMethod().getParameterAnnotation(i);
        assertEquals(1, annotationSet.size());

        DexAnnotation annotation = annotationSet.getFirst();
        if (keepUninstantiatedArguments && i == getPositionOfUnusedArgument(methodSubject)) {
          assertEquals(
              uninstantiatedClassSubject.getFinalName(),
              annotation.getAnnotationType().getTypeName());
        } else {
          assertEquals(
              instantiatedClassSubject.getFinalName(),
              annotation.getAnnotationType().getTypeName());
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
      String hello = System.currentTimeMillis() > 0 ? "Hello" : null;
      String world = System.currentTimeMillis() > 0 ? " world!" : null;
      testRemoveStaticFromStart(null, hello, world);
      testRemoveStaticFromMiddle(hello, null, world);
      testRemoveStaticFromEnd(hello, world, null);
      new TestClass().testRemoveVirtualFromStart(null, hello, world);
      new TestClass().testRemoveVirtualFromMiddle(hello, null, world);
      new TestClass().testRemoveVirtualFromEnd(hello, world, null);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    @NoParameterReordering
    static void testRemoveStaticFromStart(
        @Uninstantiated Dead uninstantiated,
        @Instantiated String instantiated,
        @Instantiated String otherInstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    @NoParameterReordering
    static void testRemoveStaticFromMiddle(
        @Instantiated String instantiated,
        @Uninstantiated Dead uninstantiated,
        @Instantiated String otherInstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    @NoParameterReordering
    static void testRemoveStaticFromEnd(
        @Instantiated String instantiated,
        @Instantiated String otherInstantiated,
        @Uninstantiated Dead uninstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    @NoParameterReordering
    void testRemoveVirtualFromStart(
        @Uninstantiated Dead uninstantiated,
        @Instantiated String instantiated,
        @Instantiated String otherInstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    @NoParameterReordering
    void testRemoveVirtualFromMiddle(
        @Instantiated String instantiated,
        @Uninstantiated Dead uninstantiated,
        @Instantiated String otherInstantiated) {
      System.out.println(instantiated + otherInstantiated);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    @NoParameterReordering
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
