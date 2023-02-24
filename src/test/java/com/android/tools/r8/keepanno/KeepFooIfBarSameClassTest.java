// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.KeepBinding;
import com.android.tools.r8.keepanno.annotations.KeepCondition;
import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepFooIfBarSameClassTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A::foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public KeepFooIfBarSameClassTest(TestParameters parameters) {
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
    assertThat(inspector.clazz(A.class), isPresent());
    assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("foo"), isPresent());
    assertThat(inspector.clazz(B.class), isPresent());
    assertThat(inspector.clazz(B.class).uniqueMethodWithOriginalName("foo"), isAbsent());
    assertThat(inspector.clazz(B.class).uniqueMethodWithOriginalName("bar"), isAbsent());
    assertThat(inspector.clazz(B.class).uniqueInstanceInitializer(), isAbsent());
  }

  @KeepEdge(consequences = {@KeepTarget(classConstant = A.class)})
  static class A {
    public void foo() {
      System.out.println("A::foo");
    }

    public void bar() throws Exception {
      getClass().getDeclaredMethod("foo").invoke(this);
    }
  }

  @KeepEdge(consequences = {@KeepTarget(classConstant = B.class)})
  static class B {
    public void foo() {
      System.out.println("B::foo");
    }

    public void bar() throws Exception {
      getClass().getDeclaredMethod("foo").invoke(this);
    }
  }

  /**
   * This conditional rule expresses that if any class in the program has a live "bar" method then
   * that same classes "foo" method is to be kept. The binding establishes the relation between the
   * holder of the two methods.
   */
  @KeepEdge(
      bindings = {@KeepBinding(bindingName = "Holder")},
      preconditions = {@KeepCondition(classFromBinding = "Holder", methodName = "bar")},
      consequences = {@KeepTarget(classFromBinding = "Holder", methodName = "foo")})
  static class TestClass {

    public static void main(String[] args) throws Exception {
      new A().bar();
    }
  }
}
