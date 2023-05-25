// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.ifs;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DoubleDiamondFloatTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DoubleDiamondFloatTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableAlwaysInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "5", "1", "1", "5", "5", "5", "1", "1", "1", "5", "5", "5", "1", "1", "1", "5");
  }

  private void inspect(CodeInspector inspector) {
    for (FoundMethodSubject method : inspector.clazz(Main.class).allMethods()) {
      if (!method.getOriginalName().equals("main")) {
        long count = method.streamInstructions().filter(InstructionSubject::isIf).count();
        assertEquals(method.getOriginalName().contains("Double") ? 2 : 1, count);
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(indirectEquals(2.0f, 6.0f));
      System.out.println(indirectEquals(3.0f, 3.0f));

      System.out.println(indirectEqualsNegated(2.0f, 6.0f));
      System.out.println(indirectEqualsNegated(3.0f, 3.0f));

      System.out.println(indirectDoubleEquals(2.0f, 6.0f, 6.0f));
      System.out.println(indirectDoubleEquals(7.0f, 7.0f, 3.0f));
      System.out.println(indirectDoubleEquals(1.0f, 1.0f, 1.0f));

      System.out.println(indirectDoubleEqualsNegated(2.0f, 6.0f, 6.0f));
      System.out.println(indirectDoubleEqualsNegated(2.0f, 2.0f, 6.0f));
      System.out.println(indirectDoubleEqualsNegated(7.0f, 7.0f, 7.0f));

      System.out.println(indirectDoubleEqualsSplit(2.0f, 6.0f, 6.0f));
      System.out.println(indirectDoubleEqualsSplit(7.0f, 7.0f, 3.0f));
      System.out.println(indirectDoubleEqualsSplit(1.0f, 1.0f, 1.0f));

      System.out.println(indirectDoubleEqualsSplitNegated(2.0f, 6.0f, 6.0f));
      System.out.println(indirectDoubleEqualsSplitNegated(2.0f, 2.0f, 6.0f));
      System.out.println(indirectDoubleEqualsSplitNegated(7.0f, 7.0f, 7.0f));
    }

    @AlwaysInline
    public static boolean doubleEqualsSplit(float i, float j, float k) {
      if (i != j) {
        return false;
      }
      return j == k;
    }

    @NeverInline
    public static int indirectDoubleEqualsSplit(float i, float j, float k) {
      if (doubleEqualsSplit(i, j, k)) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int indirectDoubleEqualsSplitNegated(float i, float j, float k) {
      if (!doubleEqualsSplit(i, j, k)) {
        return 1;
      } else {
        return 5;
      }
    }

    @AlwaysInline
    public static boolean doubleEquals(float i, float j, float k) {
      return i == j && j == k;
    }

    @NeverInline
    public static int indirectDoubleEquals(float i, float j, float k) {
      if (doubleEquals(i, j, k)) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int indirectDoubleEqualsNegated(float i, float j, float k) {
      if (!doubleEquals(i, j, k)) {
        return 1;
      } else {
        return 5;
      }
    }

    @AlwaysInline
    public static boolean equals(float i, float j) {
      return i == j;
    }

    @NeverInline
    public static int indirectEquals(float i, float j) {
      if (equals(i, j)) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int indirectEqualsNegated(float i, float j) {
      if (!equals(i, j)) {
        return 1;
      } else {
        return 5;
      }
    }
  }
}
