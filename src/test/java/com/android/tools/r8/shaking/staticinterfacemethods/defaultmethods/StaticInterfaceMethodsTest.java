// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.staticinterfacemethods.defaultmethods;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isStatic;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StaticInterfaceMethodsTest extends TestBase {

  private final TestParameters parameters;
  private final boolean allowObfuscation;

  @Parameterized.Parameters(name = "{0}, allowObfuscation: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public StaticInterfaceMethodsTest(TestParameters parameters, boolean allowObfuscation) {
    this.parameters = parameters;
    this.allowObfuscation = allowObfuscation;
  }

  private R8TestCompileResult compileTest(
      List<String> additionalKeepRules,
      ThrowingConsumer<CodeInspector, RuntimeException> inspection)
      throws Exception {
    return testForR8(parameters.getBackend())
        .addProgramClasses(InterfaceWithStaticMethods.class, TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(TestClass.class)
        .addKeepRules(additionalKeepRules)
        .enableInliningAnnotations()
        .compile()
        .inspect(inspection);
  }

  private void runTest(
      List<String> additionalKeepRules,
      ThrowingConsumer<CodeInspector, RuntimeException> inspection)
      throws Exception {
    R8TestCompileResult compileResult = compileTest(additionalKeepRules, inspection);
    Path app = compileResult.writeToZip();

    TestRunResult<?> result;
    if (allowObfuscation) {
      result =
          testForR8(parameters.getBackend())
              .addProgramClasses(InstrumentedTestClass.class)
              .addKeepAllClassesRule()
              .addApplyMapping(compileResult.getProguardMap())
              .addClasspathClasses(InterfaceWithStaticMethods.class)
              .setMinApi(parameters.getApiLevel())
              .compile()
              .addRunClasspathFiles(app)
              .run(parameters.getRuntime(), InstrumentedTestClass.class);
    } else {
      result =
          testForRuntime(parameters)
              .addProgramClasses(InstrumentedTestClass.class)
              .addClasspathFiles(app)
              .addRunClasspathFiles(app)
              .run(parameters.getRuntime(), InstrumentedTestClass.class);
    }
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      result.assertSuccessWithOutputLines("42");
    } else {
      result.assertFailure();
    }
  }

  private void interfaceNotKept(CodeInspector inspector) {
    assertFalse(inspector.clazz(InterfaceWithStaticMethods.class).isPresent());
  }

  private void staticMethodNotKept(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithStaticMethods.class);
    assertTrue(clazz.isPresent());
    assertFalse(clazz.method("int", "method", ImmutableList.of()).isPresent());
  }

  private void staticMethodKept(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithStaticMethods.class);
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("int", "method", ImmutableList.of());
    ClassSubject companionClass = clazz.toCompanionClass();
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      assertThat(method, isStatic());
      assertThat(companionClass, not(isPresent()));
    } else {
      assertThat(method, not(isPresent()));
      assertThat(companionClass, isPresent());
      assertThat(companionClass.uniqueMethodWithOriginalName("method"), isPresent());
    }
  }

  private void staticMethodKeptB160142903(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithStaticMethods.class);
    ClassSubject companionClass = clazz.toCompanionClass();
    MethodSubject method = clazz.method("int", "method", ImmutableList.of());
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      assertThat(clazz, allowObfuscation ? isPresentAndRenamed() : isPresentAndNotRenamed());
      assertThat(method, isStatic());
      assertThat(companionClass, not(isPresent()));
    } else {
      // When there is only a static method in the interface nothing is left on the interface itself
      // after desugaring, only the companion class is left.
      assertThat(clazz, not(isPresent()));
      assertThat(method, not(isPresent()));
      // TODO(160142903): The companion class should be present.
      assertThat(companionClass, not(isPresent()));
      // Also check that method exists on companion class.
    }
  }

  @Test
  public void testInterfaceNotKept() throws Exception {
    assumeTrue(!allowObfuscation); // No use of allowObfuscation.

    compileTest(ImmutableList.of(), this::interfaceNotKept);
  }

  @Test
  public void testStaticMethodNotKept() throws Exception {
    assumeTrue(!allowObfuscation); // No use of allowObfuscation.

    compileTest(
        ImmutableList.of(
            "-keep interface " + InterfaceWithStaticMethods.class.getTypeName() + "{", "}"),
        this::staticMethodNotKept);
  }

  @Test
  public void testDefaultMethodKeptWithMethods() throws Exception {
    assumeTrue(!allowObfuscation); // No use of allowObfuscation.

    compileTest(
        ImmutableList.of(
            "-keep interface " + InterfaceWithStaticMethods.class.getTypeName() + "{",
            "  <methods>;",
            "}"),
        this::staticMethodKept);
  }

  @Test
  public void testDefaultMethodKeptIndirectly() throws Exception {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    builder.add(
        "-keep class " + TestClass.class.getTypeName() + "{",
        "  public void useStaticInterfaceMethod();",
        "}");
    if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      // TODO(160142903): @NeverInline does not seem to work on static interface methods.
      // TODO(160144053): Using -keepclassmembers for this cause InterfaceWithStaticMethods to
      //  be renamed.
      if (allowObfuscation) {
        builder.add(
            "-if class " + InterfaceWithStaticMethods.class.getTypeName(),
            "-keep,allowobfuscation class " + InterfaceWithStaticMethods.class.getTypeName() + "{",
            "  public static int method();",
            "}");
      } else {
        builder.add(
            "-if class " + InterfaceWithStaticMethods.class.getTypeName(),
            "-keep class " + InterfaceWithStaticMethods.class.getTypeName() + "{",
            "  public static int method();",
            "}");
      }
    }
    runTest(builder.build(), this::staticMethodKeptB160142903);
  }

  public static class TestClass {

    public void useStaticInterfaceMethod() {
      System.out.println(InterfaceWithStaticMethods.method());
    }

    public static void main(String[] args) {}
  }

  public interface InterfaceWithStaticMethods {
    @NeverInline
    static int method() {
      return 42;
    }
  }

  public static class InstrumentedTestClass {

    public static void main(String[] args) {
      System.out.println(InterfaceWithStaticMethods.method());
    }
  }
}
