// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

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
public class UnusedAnnotatedArgumentsTest extends TestBase {

  private final boolean enableProguardCompatibilityMode;
  private final boolean keepUnusedArguments;
  private final TestParameters parameters;

  @Parameters(name = "{2}, compat: {0}, keep unused arguments: {1}")
  public static List<Object[]> params() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public UnusedAnnotatedArgumentsTest(
      boolean enableProguardCompatibilityMode,
      boolean keepUnusedArguments,
      TestParameters parameters) {
    this.enableProguardCompatibilityMode = enableProguardCompatibilityMode;
    this.keepUnusedArguments = keepUnusedArguments;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend(), enableProguardCompatibilityMode)
        .addInnerClasses(UnusedAnnotatedArgumentsTest.class)
        .addUnusedArgumentAnnotations()
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(Used.class, Unused.class)
        .addKeepAttributes("RuntimeVisibleParameterAnnotations")
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableConstantArgumentAnnotations()
        .enableUnusedArgumentAnnotations(keepUnusedArguments)
        // TODO(b/123060011): Mapping not working in presence of unused argument removal.
        .minification(keepUnusedArguments)
        .setMinApi(parameters.getApiLevel())
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
            testClassSubject.uniqueMethodWithOriginalName("testRemoveStaticFromStart"),
            testClassSubject.uniqueMethodWithOriginalName("testRemoveStaticFromMiddle"),
            testClassSubject.uniqueMethodWithOriginalName("testRemoveStaticFromEnd"),
            testClassSubject.uniqueMethodWithOriginalName("testRemoveVirtualFromStart"),
            testClassSubject.uniqueMethodWithOriginalName("testRemoveVirtualFromMiddle"),
            testClassSubject.uniqueMethodWithOriginalName("testRemoveVirtualFromEnd"));

    for (MethodSubject methodSubject : methodSubjects) {
      assertThat(methodSubject, isPresent());

      assertEquals(keepUnusedArguments ? 3 : 2, methodSubject.getMethod().getParameters().size());

      // R8 non-compat removes annotations from non-pinned items.
      assertEquals(
          enableProguardCompatibilityMode ? methodSubject.getMethod().getParameters().size() : 0,
          methodSubject.getMethod().getParameterAnnotations().size());

      for (int i = 0; i < methodSubject.getMethod().getParameterAnnotations().size(); ++i) {
        DexAnnotationSet annotationSet = methodSubject.getMethod().getParameterAnnotation(i);
        assertEquals(1, annotationSet.size());

        DexAnnotation annotation = annotationSet.annotations[0];
        if (keepUnusedArguments && i == getPositionOfUnusedArgument(methodSubject)) {
          assertEquals(
              unusedClassSubject.getFinalName(), annotation.getAnnotationType().toSourceString());
        } else {
          assertEquals(
              usedClassSubject.getFinalName(), annotation.getAnnotationType().toSourceString());
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
        @Unused String unused, @Used String used, @Used String otherUsed) {
      System.out.println(used + otherUsed);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    static void testRemoveStaticFromMiddle(
        @Used String used, @Unused String unused, @Used String otherUsed) {
      System.out.println(used + otherUsed);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    static void testRemoveStaticFromEnd(
        @Used String used, @Used String otherUsed, @Unused String unused) {
      System.out.println(used + otherUsed);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    void testRemoveVirtualFromStart(
        @Unused String unused, @Used String used, @Used String otherUsed) {
      System.out.println(used + otherUsed);
    }

    @KeepConstantArguments
    @KeepUnusedArguments
    @NeverInline
    void testRemoveVirtualFromMiddle(
        @Used String used, @Unused String unused, @Used String otherUsed) {
      System.out.println(used + otherUsed);
    }

    @KeepConstantArguments
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
