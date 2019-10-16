// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrunedOrMergedAnnotationTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PrunedOrMergedAnnotationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRewritingInFactory()
      throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addInnerClasses(PrunedOrMergedAnnotationTest.class)
        .addKeepMainRule(Main.class)
        .addKeepAttributes("*Annotation*")
        .addKeepClassAndMembersRules(Factory.class)
        .enableInliningAnnotations()
        .enableClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello", "World!")
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), not(isPresent()));
              DexType mergedType = inspector.clazz(B.class).getDexClass().type;
              ClassSubject classC = inspector.clazz(C.class);
              assertThat(classC, isPresent());
              DexEncodedAnnotation annotation =
                  classC.annotation(Factory.class.getTypeName()).getAnnotation();
              assertTrue(valueIsDexType(mergedType, annotation.elements[0].value));
              assertTrue(
                  Arrays.stream(annotation.elements[1].value.asDexValueArray().getValues())
                      .allMatch(value -> valueIsDexType(mergedType, value)));
              // Check that method parameter annotations are rewritten as well.
              DexEncodedMethod method = inspector.clazz(Main.class).mainMethod().getMethod();
              DexAnnotationSet annotationSet = method.parameterAnnotationsList.get(0);
              DexEncodedAnnotation parameterAnnotation = annotationSet.annotations[0].annotation;
              assertTrue(valueIsDexType(mergedType, parameterAnnotation.elements[0].value));
            });
  }

  private boolean valueIsDexType(DexType type, DexValue value) {
    assertTrue(value.isDexValueType());
    assertEquals(type, value.asDexValueType().value);
    return true;
  }

  public @interface Factory {

    Class<?> extending() default Object.class;

    Class<?>[] other() default Object[].class;
  }

  public static class A {}

  @NeverClassInline
  public static class B extends A {
    @NeverInline
    public void world() {
      System.out.println("World!");
    }
  }

  @Factory(
      extending = A.class,
      other = {A.class, B.class})
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
