// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.loops;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LoopWith1Iterations extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LoopWith1Iterations(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testLoopRemoved() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addDontObfuscate()
        .compile()
        .inspect(this::assertLoopRemoved)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "object",
            "loop1fori",
            "object",
            "loop1for",
            "object",
            "loop1foriAbstractValue",
            "object",
            "loop1forAbstractValue");
  }

  private void assertLoopRemoved(CodeInspector inspector) {
    inspector
        .clazz(Main.class)
        .allMethods()
        .forEach(
            m ->
                org.junit.Assert.assertTrue(
                    m.streamInstructions()
                        .noneMatch(i -> i.isArrayLength() || i.isGoto() || i.isIf())));
  }

  public static class Main {

    public static void main(String[] args) {
      loop1fori();
      loop1for();
      loop1foriAbstractValue();
      loop1forAbstractValue();
    }

    @NeverInline
    public static void loop1fori() {
      Object[] objects = new Object[1];
      for (int i = 0; i < objects.length; i++) {
        System.out.println("object");
      }
      System.out.println("loop1fori");
    }

    @NeverInline
    public static void loop1for() {
      Object[] objects = new Object[1];
      for (Object object : objects) {
        System.out.println("object");
      }
      System.out.println("loop1for");
    }

    @NeverInline
    public static Object[] getObjectArray1() {
      return new Object[1];
    }

    @NeverInline
    public static void loop1foriAbstractValue() {
      Object[] objects = getObjectArray1();
      for (int i = 0; i < objects.length; i++) {
        System.out.println("object");
      }
      System.out.println("loop1foriAbstractValue");
    }

    @NeverInline
    public static void loop1forAbstractValue() {
      Object[] objects = getObjectArray1();
      for (Object object : objects) {
        System.out.println("object");
      }
      System.out.println("loop1forAbstractValue");
    }
  }
}
