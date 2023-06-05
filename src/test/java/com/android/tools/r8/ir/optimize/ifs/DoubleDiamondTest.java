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
public class DoubleDiamondTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DoubleDiamondTest(TestParameters parameters) {
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
            "5", "1", "1", "5", "1", "5", "5", "1", "5", "5", "1", "1", "1", "5", "5", "5", "1",
            "1", "1", "5");
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
      System.out.println(indirectEquals(2, 6));
      System.out.println(indirectEquals(3, 3));

      System.out.println(indirectEqualsNegated(2, 6));
      System.out.println(indirectEqualsNegated(3, 3));

      System.out.println(indirectLessThan(2, 6));
      System.out.println(indirectLessThan(7, 3));

      System.out.println(indirectLessThanNegated(2, 6));
      System.out.println(indirectLessThanNegated(7, 3));

      System.out.println(indirectDoubleEquals(2, 6, 6));
      System.out.println(indirectDoubleEquals(7, 7, 3));
      System.out.println(indirectDoubleEquals(1, 1, 1));

      System.out.println(indirectDoubleEqualsNegated(2, 6, 6));
      System.out.println(indirectDoubleEqualsNegated(2, 2, 6));
      System.out.println(indirectDoubleEqualsNegated(7, 7, 7));

      System.out.println(indirectDoubleEqualsSplit(2, 6, 6));
      System.out.println(indirectDoubleEqualsSplit(7, 7, 3));
      System.out.println(indirectDoubleEqualsSplit(1, 1, 1));

      System.out.println(indirectDoubleEqualsSplitNegated(2, 6, 6));
      System.out.println(indirectDoubleEqualsSplitNegated(2, 2, 6));
      System.out.println(indirectDoubleEqualsSplitNegated(7, 7, 7));
    }

    @AlwaysInline
    public static boolean doubleEqualsSplit(int i, int j, int k) {
      if (i != j) {
        return false;
      }
      return j == k;
    }

    @NeverInline
    public static int indirectDoubleEqualsSplit(int i, int j, int k) {
      if (doubleEqualsSplit(i, j, k)) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int indirectDoubleEqualsSplitNegated(int i, int j, int k) {
      if (!doubleEqualsSplit(i, j, k)) {
        return 1;
      } else {
        return 5;
      }
    }

    @AlwaysInline
    public static boolean doubleEquals(int i, int j, int k) {
      return i == j && j == k;
    }

    @NeverInline
    public static int indirectDoubleEquals(int i, int j, int k) {
      if (doubleEquals(i, j, k)) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int indirectDoubleEqualsNegated(int i, int j, int k) {
      if (!doubleEquals(i, j, k)) {
        return 1;
      } else {
        return 5;
      }
    }

    @AlwaysInline
    public static boolean equals(int i, int j) {
      return i == j;
    }

    @NeverInline
    public static int indirectEquals(int i, int j) {
      if (equals(i, j)) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int indirectEqualsNegated(int i, int j) {
      if (!equals(i, j)) {
        return 1;
      } else {
        return 5;
      }
    }

    @AlwaysInline
    public static boolean lessThan(int i, int j) {
      return i <= j;
    }

    @NeverInline
    public static int indirectLessThan(int i, int j) {
      if (lessThan(i, j)) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int indirectLessThanNegated(int i, int j) {
      if (!lessThan(i, j)) {
        return 1;
      } else {
        return 5;
      }
    }
  }
}
