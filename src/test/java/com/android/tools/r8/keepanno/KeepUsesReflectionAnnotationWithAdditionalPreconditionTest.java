// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepCondition;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepUsesReflectionAnnotationWithAdditionalPreconditionTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepUsesReflectionAnnotationWithAdditionalPreconditionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithRuleExtraction() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class, B.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(B.class), isPresent());
    assertThat(inspector.clazz(B.class).uniqueMethodWithOriginalName("<init>"), isPresent());
    assertThat(inspector.clazz(B.class).uniqueMethodWithOriginalName("bar"), isPresent());
  }

  static class A {

    @UsesReflection(
        value = {
          // Ensure B's constructor and method 'bar' remain as they are invoked by reflection.
          @KeepTarget(classConstant = B.class, methodName = "<init>"),
          @KeepTarget(classConstant = B.class, methodName = "bar")
        },
        additionalPreconditions = {
          // The reflection depends on the class constant being used in the program in addition to
          // this method. In rule extraction, this will lead to an over-approximation as the rules
          // will need to keep the above live if either of the two conditions are met. There is no
          // way to express the conjunction with multiple distinct precondition classes in the
          // rule language. With direct annotation interpretation this limitation is avoided and
          // a more precise shrinking is possible.
          // TODO(b/248408342): Check this once direct interpretation is supported.
          @KeepCondition(classConstant = B.class)
        })
    public void foo(Class<B> clazz) throws Exception {
      if (clazz != null) {
        clazz.getDeclaredMethod("bar").invoke(clazz.getDeclaredConstructor().newInstance());
      }
    }
  }

  static class B {
    public static void bar() {
      System.out.println("Hello, world");
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().foo(System.nanoTime() > 0 ? B.class : null);
    }
  }
}
