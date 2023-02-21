// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.loops;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LoopWith0Iterations extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LoopWith0Iterations(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testLoopRemoved() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::assertLoopRemoved)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "loop0fori",
            "loop0for",
            "loop0foriAbstractValue",
            "loop0forAbstractValue",
            "loop0foriArgument",
            "loop0forArgument");
  }

  private void assertLoopRemoved(CodeInspector inspector) {
    inspector
        .clazz(Main.class)
        .allMethods(m -> !m.getOriginalName().contains("Argument"))
        .forEach(
            m ->
                assertTrue(
                    m.streamInstructions()
                        .noneMatch(i -> i.isArrayLength() || i.isGoto() || i.isIf())));
  }

  public static class Main {

    public static void main(String[] args) {
      loop0fori();
      loop0for();
      loop0foriAbstractValue();
      loop0forAbstractValue();
      loop0foriArgument(new Object[0]);
      loop0forArgument(new Object[0]);
    }

    @NeverInline
    public static void loop0fori() {
      Object[] objects = new Object[0];
      for (int i = 0; i < objects.length; i++) {
        System.out.println("object");
      }
      System.out.println("loop0fori");
    }

    @NeverInline
    public static void loop0for() {
      Object[] objects = new Object[0];
      for (Object object : objects) {
        System.out.println("object");
      }
      System.out.println("loop0for");
    }

    @NeverInline
    public static Object[] getObjectArray0() {
      return new Object[0];
    }

    @NeverInline
    public static void loop0foriAbstractValue() {
      Object[] objects = getObjectArray0();
      for (int i = 0; i < objects.length; i++) {
        System.out.println("object");
      }
      System.out.println("loop0foriAbstractValue");
    }

    @NeverInline
    public static void loop0forAbstractValue() {
      Object[] objects = getObjectArray0();
      for (Object object : objects) {
        System.out.println("object");
      }
      System.out.println("loop0forAbstractValue");
    }

    @NeverInline
    public static void loop0foriArgument(Object[] objects) {
      for (int i = 0; i < objects.length; i++) {
        System.out.println("object");
      }
      System.out.println("loop0foriArgument");
    }

    @NeverInline
    public static void loop0forArgument(Object[] objects) {
      for (Object object : objects) {
        System.out.println("object");
      }
      System.out.println("loop0forArgument");
    }
  }
}
