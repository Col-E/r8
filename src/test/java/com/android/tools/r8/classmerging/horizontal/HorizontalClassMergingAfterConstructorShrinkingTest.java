// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HorizontalClassMergingAfterConstructorShrinkingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
        .build();
  }

  @Test
  public void test() throws Exception {
    // TODO(b/276385221): Disabled constructor shrinking for now
    Assume.assumeTrue(parameters.canHaveNonReboundConstructorInvoke());
    assertTrue(parameters.canHaveNonReboundConstructorInvoke());
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.horizontalClassMergerOptions().disableInitialRoundOfClassMerging())
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/276385221): Should not trigger A.<init>.
        .assertSuccessWithOutputLines("Ouch!", "B");
  }

  static class Main {

    static {
      new B().setFieldOnB().printFieldOnB();
    }

    public static void main(String[] args) {
      if (System.currentTimeMillis() < 0) {
        new A().setFieldOnA().printFieldOnA();
      }
    }
  }

  @NeverClassInline
  static class A {

    Object field;

    A() {
      System.out.println("Ouch!");
    }

    @NeverInline
    A setFieldOnA() {
      field = "A";
      return this;
    }

    @NeverInline
    void printFieldOnA() {
      System.out.println(field);
    }
  }

  @NeverClassInline
  static class B {

    Object field;

    // Removed by constructor shrinking.
    B() {}

    @NeverInline
    B setFieldOnB() {
      field = "B";
      return this;
    }

    @NeverInline
    void printFieldOnB() {
      System.out.println(field);
    }
  }
}
