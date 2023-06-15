// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.constantpropagation;

import static com.android.tools.r8.ir.optimize.constantpropagation.KotlinDefaultArgumentsTest.Greeter.getHello;
import static com.android.tools.r8.ir.optimize.constantpropagation.KotlinDefaultArgumentsTest.Greeter.getWorld;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinDefaultArgumentsTest extends TestBase {

  enum Effect {
    MATERIALIZED,
    NONE,
    REMOVED
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testFirstBranchRemoved() throws Exception {
    test(MainRemovedNone.class, Effect.REMOVED, Effect.NONE);
  }

  @Test
  public void testBothBranchesRemoved() throws Exception {
    test(MainRemovedRemoved.class, Effect.REMOVED, Effect.REMOVED);
  }

  @Test
  public void testFirstBranchRemovedAndSecondBranchMaterialized() throws Exception {
    test(MainRemovedMaterialized.class, Effect.REMOVED, Effect.MATERIALIZED);
  }

  @Test
  public void testFirstBranchMaterialized() throws Exception {
    test(MainMaterializedNone.class, Effect.MATERIALIZED, Effect.NONE);
  }

  @Test
  public void testFirstBranchMaterializedAndSecondBranchRemoved() throws Exception {
    test(MainMaterializedRemoved.class, Effect.MATERIALIZED, Effect.REMOVED);
  }

  @Test
  public void testBothBranchesMaterialized() throws Exception {
    test(MainMaterializedMaterialized.class, Effect.MATERIALIZED, Effect.MATERIALIZED);
  }

  private void test(Class<?> mainClass, Effect firstBranchEffect, Effect secondBranchEffect)
      throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(mainClass, Greeter.class)
        .addKeepMainRule(mainClass)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // TODO(b/196017578): If the branch effect is NONE, then check that the IF instruction
              //  AND its body are retained.
              // TODO(b/196017578): If the branch effect is REMOVED, then check that the IF
              //  instruction AND its body is removed.
              // TODO(b/196017578): If the branch effect is MATERIALIZED, then check that the IF
              //  instruction is removed but NOT its body.
              // TODO(b/196017578): If the branch effect is MATERIALIZED, then check that the unused
              //  parameter is removed.
            })
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  // Test where first parameter X is always given at the call site.
  // The first branch in greet() should be removed.
  static class MainRemovedNone {

    public static void main(String[] args) {
      Greeter.greet(getHello(), null, 2);
      if (System.currentTimeMillis() < 0) {
        Greeter.greet(getHello(), getWorld(), 0);
      }
    }
  }

  // Test where both parameters X and Y are always given at the call site.
  // Both branches in greet() should be removed.
  static class MainRemovedRemoved {

    public static void main(String[] args) {
      Greeter.greet(getHello(), getWorld(), 0);
    }
  }

  // Test where the first parameter X is always given and the second parameter Y is never given at
  // the call site.
  // The first branch in greet() should be removed and the second branch in greet() should be
  // "materialized".
  static class MainRemovedMaterialized {

    public static void main(String[] args) {
      Greeter.greet(getHello(), null, 2);
    }
  }

  // Test where the first parameter X is never given at the call site.
  // The first branch in greet() should be materialized.
  static class MainMaterializedNone {

    public static void main(String[] args) {
      Greeter.greet(null, null, 3);
      if (System.currentTimeMillis() < 0) {
        Greeter.greet(null, getWorld(), 1);
      }
    }
  }

  // Test where the first parameter X is never given and the second parameter Y is always given at
  // the call site.
  // The first branch in greet() should be materialized and the second branch should be removed.
  static class MainMaterializedRemoved {

    public static void main(String[] args) {
      Greeter.greet(null, getWorld(), 1);
    }
  }

  // Test where none of the parameters X and Y are given at the call site.
  // Both branches in greet() should be materialized.
  static class MainMaterializedMaterialized {

    public static void main(String[] args) {
      Greeter.greet(null, null, 3);
    }
  }

  static class Greeter {

    @NeverInline
    static void greet(String x, String y, int defaults) {
      if ((defaults & 1) != 0) {
        x = "Hello";
      }
      if ((defaults & 2) != 0) {
        y = ", world!";
      }
      System.out.print(x);
      System.out.println(y);
    }

    @NeverInline
    static String getHello() {
      return System.currentTimeMillis() > 0 ? "Hello" : "";
    }

    @NeverInline
    static String getWorld() {
      return System.currentTimeMillis() > 0 ? ", world!" : "";
    }
  }
}
