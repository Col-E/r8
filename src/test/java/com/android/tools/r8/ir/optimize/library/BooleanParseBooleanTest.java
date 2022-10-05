// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BooleanParseBooleanTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public BooleanParseBooleanTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClasses(TestClass.class)
        .release()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true", "true", "true", "false", "false", "false", "true");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("true", "true", "true", "false", "false", "false", "true");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());

    MethodSubject testOptimizedMethodSubject =
        testClassSubject.uniqueMethodWithOriginalName("testOptimized");
    assertThat(testOptimizedMethodSubject, isPresent());
    assertTrue(
        testOptimizedMethodSubject
            .streamInstructions()
            .filter(InstructionSubject::isInvokeStatic)
            .map(InstructionSubject::getMethod)
            .map(DexMethod::toSourceString)
            .noneMatch(method -> method.contains("parseBoolean")));

    MethodSubject testNotOptimizedMethodSubject =
        testClassSubject.uniqueMethodWithOriginalName("testNotOptimized");
    assertThat(testNotOptimizedMethodSubject, isPresent());
    assertEquals(
        1,
        testNotOptimizedMethodSubject
            .streamInstructions()
            .filter(InstructionSubject::isInvokeStatic)
            .map(InstructionSubject::getMethod)
            .map(DexMethod::toSourceString)
            .filter(method -> method.contains("parseBoolean"))
            .count());
  }

  static class TestClass {

    public static void main(String[] args) {
      testOptimized();
      testNotOptimized();
    }

    @NeverInline
    static void testOptimized() {
      System.out.println(Boolean.parseBoolean("true"));
      System.out.println(Boolean.parseBoolean("tRuE"));
      System.out.println(Boolean.parseBoolean("TRUE"));
      System.out.println(Boolean.parseBoolean("false"));
      System.out.println(Boolean.parseBoolean("fAlSe"));
      System.out.println(Boolean.parseBoolean("FALSE"));
    }

    @NeverInline
    static void testNotOptimized() {
      System.out.println(Boolean.parseBoolean(unknown()));
    }

    static String unknown() {
      return System.currentTimeMillis() >= 0 ? "true" : "false";
    }
  }
}
