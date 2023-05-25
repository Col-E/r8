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
public class DoubleDiamondCstTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public DoubleDiamondCstTest(TestParameters parameters) {
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
            "1", "5", "5", "1", "1", "5", "5", "1", "5", "5", "1", "1", "1", "5");
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
      System.out.println(indirectTest(2, 6));
      System.out.println(indirectTest(3, 3));

      System.out.println(indirectTestNegated(2, 6));
      System.out.println(indirectTestNegated(3, 3));

      System.out.println(indirectCmp(2, 6));
      System.out.println(indirectCmp(7, 3));

      System.out.println(indirectCmpNegated(2, 6));
      System.out.println(indirectCmpNegated(7, 3));

      System.out.println(indirectDoubleTest(2, 6, 6));
      System.out.println(indirectDoubleTest(7, 7, 3));
      System.out.println(indirectDoubleTest(1, 1, 1));

      System.out.println(indirectDoubleTestNegated(2, 6, 6));
      System.out.println(indirectDoubleTestNegated(2, 2, 6));
      System.out.println(indirectDoubleTestNegated(7, 7, 7));
    }

    @AlwaysInline
    public static int doubleTest(int i, int j, int k) {
      if (i != j) {
        return 1;
      }
      if (j == k) {
        return 2;
      }
      return 3;
    }

    @NeverInline
    public static int indirectDoubleTest(int i, int j, int k) {
      if (doubleTest(i, j, k) == 2) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int indirectDoubleTestNegated(int i, int j, int k) {
      if (doubleTest(i, j, k) != 2) {
        return 1;
      } else {
        return 5;
      }
    }

    @AlwaysInline
    public static int test(int i, int j) {
      return i == j ? 1 : 2;
    }

    @NeverInline
    public static int indirectTest(int i, int j) {
      if (test(i, j) == 2) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int indirectTestNegated(int i, int j) {
      if (test(i, j) != 2) {
        return 1;
      } else {
        return 5;
      }
    }

    @AlwaysInline
    public static int cmp(int i, int j) {
      return i <= j ? 1 : 2;
    }

    @NeverInline
    public static int indirectCmp(int i, int j) {
      if (cmp(i, j) < 2) {
        return 1;
      } else {
        return 5;
      }
    }

    @NeverInline
    public static int indirectCmpNegated(int i, int j) {
      if (cmp(i, j) > 1) {
        return 1;
      } else {
        return 5;
      }
    }
  }
}
