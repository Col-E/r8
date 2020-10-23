// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumenosideeffects;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeNoSideEffectsForJavaLangClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AssumeNoSideEffectsForJavaLangClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableSideEffectAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    inspectMethod(testClassSubject.uniqueMethodWithName("testModelingOfSideEffects"), false, false);
    inspectMethod(
        testClassSubject.uniqueMethodWithName("testModelingOfSideEffectsMaybeNull"), true, false);
    inspectMethod(
        testClassSubject.uniqueMethodWithName("testModelingOfSideEffectsMaybeSubclass"),
        false,
        true);
  }

  private void inspectMethod(
      MethodSubject methodSubject, boolean maybeNullReceiver, boolean maybeSubtype) {
    assertThat(methodSubject, isPresent());
    assertThat(
        methodSubject, onlyIf(maybeNullReceiver || maybeSubtype, invokesMethodWithName("equals")));
    assertThat(
        methodSubject,
        onlyIf(maybeNullReceiver || maybeSubtype, invokesMethodWithName("hashCode")));
    assertThat(methodSubject, onlyIf(maybeNullReceiver, invokesMethodWithName("getClass")));
    assertThat(
        methodSubject,
        onlyIf(maybeNullReceiver || maybeSubtype, invokesMethodWithName("toString")));
  }

  static class TestClass {

    public static void main(String[] args) {
      testModelingOfSideEffects();
      testModelingOfSideEffectsMaybeNull();
      testModelingOfSideEffectsMaybeSubclass();
    }

    @AssumeMayHaveSideEffects
    @NeverInline
    static void testModelingOfSideEffects() {
      Object o = new Object();
      o.equals(new Object());
      o.hashCode();
      o.getClass();
      o.toString();
    }

    @NeverInline
    static void testModelingOfSideEffectsMaybeNull() {
      createNullableObject().equals(new Object());
      createNullableObject().hashCode();
      createNullableObject().getClass();
      createNullableObject().toString();
    }

    @NeverInline
    static void testModelingOfSideEffectsMaybeSubclass() {
      Object o = System.currentTimeMillis() > 0 ? new Object() : new A();
      o.equals(new Object());
      o.hashCode();
      o.getClass();
      o.toString();
    }

    static Object createNullableObject() {
      return System.currentTimeMillis() > 0 ? new Object() : null;
    }
  }

  static class A {

    public A() {
      super();
    }

    @Override
    public boolean equals(Object obj) {
      throw new RuntimeException();
    }

    @Override
    public int hashCode() {
      throw new RuntimeException();
    }

    @Override
    public String toString() {
      throw new RuntimeException();
    }
  }
}
