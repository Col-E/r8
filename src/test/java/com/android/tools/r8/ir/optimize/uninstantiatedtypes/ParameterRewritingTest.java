// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ParameterRewritingTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "Factory.createStatic() -> null",
          "Factory.createStaticWithUnused1() -> null",
          "Factory.createStaticWithUnused2() -> null",
          "Factory.createStaticWithUnused3() -> null",
          "Factory.createStaticWithUnused4() -> null");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(ParameterRewritingTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .addOptionsModification(options -> options.enableClassInlining = false)
            .addDontObfuscate()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED_OUTPUT)
            .inspector();

    ClassSubject factoryClassSubject = inspector.clazz(Factory.class);
    MethodSubject createStaticMethodSubject =
        factoryClassSubject.uniqueMethodWithOriginalName("createStatic");
    assertThat(createStaticMethodSubject, isPresent());
    assertEquals(1, createStaticMethodSubject.getMethod().getParameters().size());

    for (int i = 1; i <= 3; ++i) {
      String createStaticWithUnusedMethodName = "createStaticWithUnused" + i;
      MethodSubject createStaticWithUnusedMethodSubject =
          factoryClassSubject.uniqueMethodWithOriginalName(createStaticWithUnusedMethodName);
      assertThat(createStaticWithUnusedMethodSubject, isPresent());

      DexMethod method = createStaticWithUnusedMethodSubject.getMethod().getReference();
      assertEquals(1, method.getParameters().size());
      assertEquals("java.lang.String", method.getParameters().toString());
    }

    MethodSubject createStaticWithUnusedMethodSubject =
        factoryClassSubject.uniqueMethodWithOriginalName("createStaticWithUnused4");
    assertThat(createStaticWithUnusedMethodSubject, isPresent());

    DexMethod method = createStaticWithUnusedMethodSubject.getMethod().getReference();
    assertEquals(3, method.getParameters().size());
    assertEquals(
        "java.lang.String java.lang.String java.lang.String", method.getParameters().toString());

    assertThat(inspector.clazz(Uninstantiated.class), not(isPresent()));
  }

  static class TestClass {

    public static void main(String[] args) {
      Object obj1 = Factory.createStatic(null, asNonConstantString("Factory.createStatic()"));
      System.out.println(" -> " + obj1);

      Object obj2 =
          Factory.createStaticWithUnused1(
              new Object(), null, asNonConstantString("Factory.createStaticWithUnused1()"));
      System.out.println(" -> " + obj2);

      Object obj3 =
          Factory.createStaticWithUnused2(
              null, new Object(), asNonConstantString("Factory.createStaticWithUnused2()"));
      System.out.println(" -> " + obj3);

      Object obj4 =
          Factory.createStaticWithUnused3(
              null, asNonConstantString("Factory.createStaticWithUnused3()"), new Object());
      System.out.println(" -> " + obj4);

      Object obj5 =
          Factory.createStaticWithUnused4(
              asNonConstantString("Factory"),
              new Object(),
              null,
              asNonConstantString("."),
              new Object(),
              null,
              asNonConstantString("createStaticWithUnused4()"));
      System.out.println(" -> " + obj5);
    }

    public static String asNonConstantString(String string) {
      return System.currentTimeMillis() > 0 ? string : null;
    }
  }

  @NoHorizontalClassMerging
  static class Uninstantiated {}

  @NoHorizontalClassMerging
  static class Factory {

    @NeverInline
    public static Object createStatic(Uninstantiated uninstantiated, String msg) {
      System.out.print(msg);
      return uninstantiated;
    }

    @NeverInline
    public static Object createStaticWithUnused1(
        Object unused, Uninstantiated uninstantiated, String msg) {
      System.out.print(msg);
      return uninstantiated;
    }

    @NeverInline
    public static Object createStaticWithUnused2(
        Uninstantiated uninstantiated, Object unused, String msg) {
      System.out.print(msg);
      return uninstantiated;
    }

    @NeverInline
    public static Object createStaticWithUnused3(
        Uninstantiated uninstantiated, String msg, Object unused) {
      System.out.print(msg);
      return uninstantiated;
    }

    @NeverInline
    public static Object createStaticWithUnused4(
        String msg1,
        Object unused1,
        Uninstantiated uninstantiated1,
        String msg2,
        Object unused2,
        Uninstantiated uninstantiated2,
        String msg3) {
      System.out.print(msg1 + msg2 + msg3);
      return oneOf(uninstantiated1, uninstantiated2);
    }

    @NeverInline
    private static <T> T oneOf(T x, T y) {
      return System.currentTimeMillis() > 0 ? x : y;
    }
  }
}
