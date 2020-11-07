// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class GetClassTest extends ReflectionOptimizerTestBase {

  static class Base {}

  static class Sub extends Base {}

  @NoHorizontalClassMerging
  static class EffectivelyFinal {}

  static class Reflection implements Callable<Class<?>> {

    @ForceInline
    @Override
    public Class<?> call() {
      return getClass();
    }
  }

  @NoHorizontalClassMerging
  static class GetClassTestMain implements Callable<Class<?>> {

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
  }

  static class Main {

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
        System.out.println(GetClassTestMain.getMainClass(instance));

        System.out.println(GetClassTestMain.getMainClass(null));
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

  private static final String JAVA_OUTPUT =
      StringUtils.lines(
          ListUtils.map(
              ImmutableList.of(
                  Base.class.getTypeName(),
                  Base.class.getTypeName(),
                  Sub.class.getTypeName(),
                  "[L" + Sub.class.getTypeName() + ";",
                  EffectivelyFinal.class.getTypeName(),
                  GetClassTestMain.class.getTypeName(),
                  GetClassTestMain.class.getTypeName(),
                  Reflection.class.getTypeName(),
                  Reflection.class.getTypeName()),
              l -> "class " + l));

  private static final Class<?> MAIN = Main.class;

  @Parameterized.Parameters(name = "{0}, mode:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), CompilationMode.values());
  }

  private final TestParameters parameters;
  private final CompilationMode mode;

  public GetClassTest(TestParameters parameters, CompilationMode mode) {
    this.parameters = parameters;
    this.mode = mode;
  }

  private void configure(InternalOptions options) {
    // In `getMainClass`, a call with `null`, which will throw NPE, is replaced with null throwing
    // code. Then, remaining call with non-null argument made getClass() replaceable.
    // Disable the propagation of call site information to separate the tests.
    options.callSiteOptimizationOptions().disableTypePropagationForTesting();
  }

  @Test
  public void testJVM() throws Exception {
    assumeTrue(
        "Only run JVM reference on CF runtimes",
        parameters.isCfRuntime() && mode == CompilationMode.DEBUG);
    testForJvm()
        .addInnerClasses(GetClassTest.class)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void test(
      CodeInspector codeInspector,
      boolean expectCallPresent,
      int expectedGetClassCount,
      int expectedConstClassCount) {
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedGetClassCount, countGetClass(mainMethod));
    assertEquals(expectedConstClassCount, countConstClass(mainMethod));

    ClassSubject getterClass = codeInspector.clazz(GetClassTestMain.class);
    MethodSubject getMainClass = getterClass.uniqueMethodWithName("getMainClass");
    assertThat(getMainClass, isPresent());
    // Because of nullable argument, getClass() should remain.
    assertEquals(1, countGetClass(getMainClass));
    assertEquals(0, countConstClass(getMainClass));

    MethodSubject call = getterClass.method("java.lang.Class", "call", ImmutableList.of());
    if (!expectCallPresent) {
      assertThat(call, not(isPresent()));
    } else {
      assertThat(call, isPresent());
      // Because of local, only R8 release mode can rewrite getClass() to const-class.
      assertEquals(1, countGetClass(call));
      assertEquals(0, countConstClass(call));
    }
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());
    testForD8()
        .setMode(mode)
        .addInnerClasses(GetClassTest.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT)
        .inspect(inspector -> test(inspector, true, 6, 0));
  }

  @Test
  public void testR8() throws Exception {
    boolean isRelease = mode == CompilationMode.RELEASE;
    boolean expectCallPresent = !isRelease;
    int expectedGetClassCount = isRelease ? 0 : 5;
    int expectedConstClassCount = isRelease ? (parameters.isCfRuntime() ? 8 : 6) : 1;
    testForR8(parameters.getBackend())
        .setMode(mode)
        .addInnerClasses(GetClassTest.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .addKeepMainRule(MAIN)
        .noMinification()
        .addOptionsModification(this::configure)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT)
        .inspect(
            inspector ->
                test(inspector, expectCallPresent, expectedGetClassCount, expectedConstClassCount));
  }
}
