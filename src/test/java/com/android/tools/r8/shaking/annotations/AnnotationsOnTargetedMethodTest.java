// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AnnotationsOnTargetedMethodTest extends TestBase {

  private static final String expectedOutput =
      StringUtils.lines(
          "In InterfaceImpl.targetedMethod()",
          "In OtherInterfaceImpl.targetedMethod()",
          MyAnnotation.class.getName());

  private static final String expectedOutputWithAnnotationRemoval =
      StringUtils.lines(
          "In InterfaceImpl.targetedMethod()", "In OtherInterfaceImpl.targetedMethod()");

  private final boolean enableProguardCompatibilityMode;
  private final boolean keepAllowShrinking;
  private final TestParameters parameters;

  @Parameters(name = "{2}, compat: {0}, keep: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public AnnotationsOnTargetedMethodTest(
      boolean enableProguardCompatibilityMode,
      boolean keepAllowShrinking,
      TestParameters parameters) {
    this.enableProguardCompatibilityMode = enableProguardCompatibilityMode;
    this.keepAllowShrinking = keepAllowShrinking;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // No need to run R8 compat mode with extra -keep,allowshrinking rule.
    assumeTrue(!enableProguardCompatibilityMode || !keepAllowShrinking);
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addTestClasspath()
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }
    testForR8Compat(parameters.getBackend(), enableProguardCompatibilityMode)
        .addInnerClasses(AnnotationsOnTargetedMethodTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRuntimeVisibleAnnotations()
        .applyIf(
            keepAllowShrinking,
            builder -> {
              // Add extra rule to retain the annotation on Interface.targetedMethod() in non-compat
              // mode.
              assertFalse(enableProguardCompatibilityMode); // See assumeTrue() above.
              builder.addKeepRules(
                  "-keepclassmembers,allowshrinking class " + Interface.class.getTypeName() + " {",
                  "  void targetedMethod();",
                  "}");
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addDontObfuscate()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(
            !enableProguardCompatibilityMode && !keepAllowShrinking
                ? expectedOutputWithAnnotationRemoval
                : expectedOutput);
  }

  static class TestClass {

    public static void main(String[] args) {
      test(new InterfaceImpl());
      test(new OtherInterfaceImpl());

      Method method = Interface.class.getDeclaredMethods()[0];
      for (Annotation annotation : method.getAnnotations()) {
        visitAnnotation((MyAnnotation) annotation);
      }
    }

    @NeverInline
    private static void test(Interface obj) {
      obj.targetedMethod();
    }

    @NeverInline
    private static void visitAnnotation(MyAnnotation annotation) {
      System.out.println(annotation.annotationType().getName());
    }
  }

  @NoVerticalClassMerging
  interface Interface {

    @NeverInline
    @MyAnnotation
    void targetedMethod();
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class InterfaceImpl implements Interface {

    @NeverInline
    @Override
    public void targetedMethod() {
      System.out.println("In InterfaceImpl.targetedMethod()");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class OtherInterfaceImpl implements Interface {

    @NeverInline
    @Override
    public void targetedMethod() {
      System.out.println("In OtherInterfaceImpl.targetedMethod()");
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD})
  @interface MyAnnotation {}
}
