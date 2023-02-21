// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrunedOrMergedAnnotationTest extends TestBase {

  private final boolean enableProguardCompatibilityMode;
  private final boolean keepForAnnotations;
  private final TestParameters parameters;

  @Parameters(name = "{2}, compat: {0}, keep: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public PrunedOrMergedAnnotationTest(
      boolean enableProguardCompatibilityMode,
      boolean keepForAnnotations,
      TestParameters parameters) {
    this.enableProguardCompatibilityMode = enableProguardCompatibilityMode;
    this.keepForAnnotations = keepForAnnotations;
    this.parameters = parameters;
  }

  @Test
  public void testRewritingInFactory()
      throws IOException, CompilationFailedException, ExecutionException {
    // No need to add extra keep rules for retaining annotations in compat mode.
    assumeTrue(!enableProguardCompatibilityMode || !keepForAnnotations);
    testForR8Compat(parameters.getBackend(), enableProguardCompatibilityMode)
        .addInnerClasses(PrunedOrMergedAnnotationTest.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Factory.class)
        .addKeepRuntimeInvisibleAnnotations()
        .addKeepRuntimeInvisibleParameterAnnotations()
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(A.class))
        .applyIf(
            keepForAnnotations,
            builder -> {
              assertFalse(enableProguardCompatibilityMode);
              builder.addKeepRules(
                  "-keep,allowshrinking,allowobfuscation class " + C.class.getTypeName());
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello", "World!")
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), not(isPresent()));
              DexType mergedType = inspector.clazz(B.class).getDexProgramClass().type;
              ClassSubject classC = inspector.clazz(C.class);
              assertThat(classC, isPresent());

              MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
              if (enableProguardCompatibilityMode || keepForAnnotations) {
                DexEncodedAnnotation annotation =
                    classC.annotation(Factory.class.getTypeName()).getAnnotation();
                assertTrue(valueIsDexType(mergedType, annotation.elements[0].value));
                assertTrue(
                    Arrays.stream(annotation.elements[1].value.asDexValueArray().getValues())
                        .allMatch(value -> valueIsDexType(mergedType, value)));
              } else {
                assertTrue(classC.getDexProgramClass().annotations().isEmpty());
              }

              // Check that method parameter annotations are rewritten.
              DexAnnotationSet annotationSet = mainMethod.getMethod().getParameterAnnotation(0);
              DexEncodedAnnotation parameterAnnotation = annotationSet.annotations[0].annotation;
              assertTrue(valueIsDexType(mergedType, parameterAnnotation.elements[0].value));
            });
  }

  private boolean valueIsDexType(DexType type, DexValue value) {
    assertTrue(value.isDexValueType());
    assertEquals(type, value.asDexValueType().value);
    return true;
  }

  @Retention(RetentionPolicy.CLASS)
  public @interface Factory {

    Class<?> extending() default Object.class;

    Class<?>[] other() default Object[].class;
  }

  public static class A {}

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class B extends A {
    @NeverInline
    public void world() {
      System.out.println("World!");
    }
  }

  @Factory(
      extending = A.class,
      other = {A.class, B.class})
  @NoHorizontalClassMerging
  public static class C {
    @NeverInline
    public static void hello() {
      System.out.println("Hello");
    }
  }

  public static class Main {

    public static void main(@Factory(extending = A.class) String[] args) {
      C.hello();
      new B().world();
    }
  }
}
