// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.Callable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class GetClassTestMain implements Callable<Class<?>> {
  static class Base {}
  static class Sub extends Base {}
  static class EffectivelyFinal {}

  static class Reflection implements Callable<Class<?>> {
    @ForceInline
    @Override
    public Class<?> call() {
      return getClass();
    }
  }

  @NeverInline
  static Class<?> getMainClass(GetClassTestMain instance) {
    // Nullable argument. Should not be rewritten to const-class to preserve NPE.
    return instance.getClass();
  }

  @NeverInline
  @Override
  public Class<?> call() {
    // Non-null `this` pointer.
    return getClass();
  }

  public static void main(String[] args) {
    {
      Base base = new Base();
      // Not applicable in debug mode.
      System.out.println(base.getClass());
      // Can be rewritten to const-class always.
      System.out.println(new Base().getClass());
    }

    {
      Base sub = new Sub();
      // Not applicable in debug mode.
      System.out.println(sub.getClass());
    }

    {
      Base[] subs = new Sub[1];
      // Not applicable in debug mode.
      System.out.println(subs.getClass());
    }

    {
      EffectivelyFinal ef = new EffectivelyFinal();
      // Not applicable in debug mode.
      System.out.println(ef.getClass());
    }

    try {
      // To not be recognized as un-instantiated class.
      GetClassTestMain instance = new GetClassTestMain();
      System.out.println(instance.call());
      System.out.println(getMainClass(instance));

      System.out.println(getMainClass(null));
      throw new AssertionError("Should preserve NPE.");
    } catch (NullPointerException e) {
      // Expected
    }

    {
      Reflection r = new Reflection();
      // Not applicable in debug mode.
      System.out.println(r.getClass());
      try {
        // Can be rewritten to const-class after inlining.
        System.out.println(r.call());
      } catch (Throwable e) {
        throw new AssertionError("Not expected any exceptions.");
      }
    }
  }
}

@RunWith(Parameterized.class)
public class GetClassTest extends ReflectionOptimizerTestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Base",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Base",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Sub",
      "class [Lcom.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Sub;",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$EffectivelyFinal",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Reflection",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Reflection"
  );
  private static final Class<?> MAIN = GetClassTestMain.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public GetClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void configure(InternalOptions options) {
    // In `getMainClass`, a call with `null`, which will throw NPE, is replaced with null throwing
    // code. Then, remaining call with non-null argument made getClass() replaceable.
    // Disable the propagation of call site information to separate the tests.
    options.enablePropagationOfDynamicTypesAtCallSites = false;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void test(
      TestRunResult<?> result, boolean isR8, boolean isRelease) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    int expectedCount = isR8 ? (isRelease ? 0 : 5) : 6;
    assertEquals(expectedCount, countGetClass(mainMethod));
    expectedCount = isR8 ? (isRelease ? (parameters.isCfRuntime() ? 7 : 5) : 1) : 0;
    assertEquals(expectedCount, countConstClass(mainMethod));

    boolean expectToBeOptimized = isR8 && isRelease;

    MethodSubject getMainClass = mainClass.uniqueMethodWithName("getMainClass");
    assertThat(getMainClass, isPresent());
    // Because of nullable argument, getClass() should remain.
    assertEquals(1, countGetClass(getMainClass));
    assertEquals(0, countConstClass(getMainClass));

    MethodSubject call = mainClass.method("java.lang.Class", "call", ImmutableList.of());
    assertThat(call, isPresent());
    // Because of local, only R8 release mode can rewrite getClass() to const-class.
    assertEquals(expectToBeOptimized ? 0 : 1, countGetClass(call));
    assertEquals(expectToBeOptimized ? 1 : 0, countConstClass(call));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    // D8 debug.
    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClassesAndInnerClasses(MAIN)
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, false, false);

    // D8 release.
    result =
        testForD8()
            .release()
            .addProgramClassesAndInnerClasses(MAIN)
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, false, true);
  }

  @Test
  public void testR8() throws Exception {
    // R8 debug, no minification.
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .debug()
            .addProgramClassesAndInnerClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .noMinification()
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), MAIN);
    test(result, true, false);

    // R8 release, no minification.
    result =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .noMinification()
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, true, true);

    // R8 release, minification.
    result =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(MAIN)
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .addOptionsModification(this::configure)
            .setMinApi(parameters.getRuntime())
            // We are not checking output because it can't be matched due to minification. Just run.
            .run(parameters.getRuntime(), MAIN);
    test(result, true, true);
  }
}
