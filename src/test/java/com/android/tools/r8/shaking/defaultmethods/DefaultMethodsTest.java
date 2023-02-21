// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.defaultmethods;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultMethodsTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DefaultMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void runTest(
      List<String> additionalKeepRules,
      ThrowingConsumer<CodeInspector, RuntimeException> inspection)
      throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            InterfaceWithDefaultMethods.class,
            ClassImplementingInterface.class,
            OtherClassImplementingInterface.class,
            TestClass.class)
        .setMinApi(parameters)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(additionalKeepRules)
        .addDontObfuscate()
        .compile()
        .inspect(inspection);
  }

  private void interfaceNotKept(CodeInspector inspector) {
    assertFalse(inspector.clazz(InterfaceWithDefaultMethods.class).isPresent());
  }

  private void defaultMethodNotKept(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithDefaultMethods.class);
    assertThat(clazz, isPresent());
    assertThat(clazz.method("int", "method", ImmutableList.of()), not(isPresent()));
  }

  private void defaultMethodKept(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithDefaultMethods.class);
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("int", "method", ImmutableList.of());
    assertThat(method, isPresent());
    ClassSubject companionClass = clazz.toCompanionClass();
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      assertThat(method, not(isAbstract()));
      assertThat(companionClass, not(isPresent()));
    } else {
      assertThat(method, isAbstract());
      assertThat(companionClass, isPresent());
      MethodSubject defaultMethod = method.toMethodOnCompanionClass();
      assertThat(defaultMethod, isPresent());
    }
  }

  private void defaultMethodKeptWithoutCompanionClass(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithDefaultMethods.class);
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("int", "method", ImmutableList.of());
    assertThat(method, isPresent());
    ClassSubject companionClass = clazz.toCompanionClass();
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      assertThat(method, not(isAbstract()));
    } else {
      assertThat(method, isAbstract());
    }
    assertThat(companionClass, not(isPresent()));
  }

  @Test
  public void testInterfaceNotKept() throws Exception {
    runTest(ImmutableList.of(), this::interfaceNotKept);
  }

  @Test
  public void testDefaultMethodNotKept() throws Exception {
    runTest(
        ImmutableList.of(
            "-keep interface " + InterfaceWithDefaultMethods.class.getTypeName() + "{", "}"),
        this::defaultMethodNotKept);
  }

  @Test
  public void testDefaultMethodKeptWithMethods() throws Exception {
    runTest(
        ImmutableList.of(
            "-keep interface " + InterfaceWithDefaultMethods.class.getTypeName() + "{",
            "  <methods>;",
            "}"),
        this::defaultMethodKept);
  }

  @Test
  public void testDefaultMethodsKeptExplicitly() throws Exception {
    runTest(
        ImmutableList.of(
            "-keep interface " + InterfaceWithDefaultMethods.class.getTypeName() + "{",
            "  public int method();",
            "}"),
        this::defaultMethodKept);
  }

  @Test
  public void testDefaultMethodNotKeptIndirectly() throws Exception {
    runTest(
        ImmutableList.of(
            "-keep class " + ClassImplementingInterface.class.getTypeName() + "{",
            "  <methods>;",
            "}",
            // Prevent InterfaceWithDefaultMethods from being merged into ClassImplementingInterface
            "-keep class " + InterfaceWithDefaultMethods.class.getTypeName()),
        this::defaultMethodNotKept);
  }

  @Test
  public void testDefaultMethodKeptIndirectly() throws Exception {
    runTest(
        ImmutableList.of(
            "-keep class " + ClassImplementingInterface.class.getTypeName() + "{",
            "  <methods>;",
            "}",
            "-keep class " + TestClass.class.getCanonicalName() + "{",
            "  public void useInterfaceMethod();",
            "}",
            // Prevent InterfaceWithDefaultMethods from being merged into ClassImplementingInterface
            "-keep class " + InterfaceWithDefaultMethods.class.getTypeName()),
        this::defaultMethodKeptWithoutCompanionClass);
  }
}
