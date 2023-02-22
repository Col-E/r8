// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EffectivelyFinalStaticFieldsTest extends TestBase {
  private static final Class<?> MAIN = TestClass.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testJVMOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("The end");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EffectivelyFinalStaticFieldsTest.class)
        .addKeepMainRule(MAIN)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            codeInspector -> {
              ClassSubject main = codeInspector.clazz(MAIN);
              assertThat(main, isPresent());

              MethodSubject mainMethod = main.mainMethod();
              assertThat(mainMethod, isPresent());

              assertTrue(
                  mainMethod
                      .streamInstructions()
                      .noneMatch(i -> i.isConstString("Dead code: 1", JumboStringMode.ALLOW)));
              assertTrue(
                  mainMethod
                      .streamInstructions()
                      .noneMatch(i -> i.isConstString("Dead code: 2", JumboStringMode.ALLOW)));
              // TODO(b/138913138): not trivial; assigned multiple times, but can determine the
              // value.
              assertFalse(
                  mainMethod
                      .streamInstructions()
                      .noneMatch(i -> i.isConstString("Dead code: 3", JumboStringMode.ALLOW)));
              assertTrue(
                  mainMethod
                      .streamInstructions()
                      .noneMatch(i -> i.isConstString("Dead code: 4", JumboStringMode.ALLOW)));
              assertTrue(
                  mainMethod
                      .streamInstructions()
                      .noneMatch(i -> i.isConstString("Dead code: 5", JumboStringMode.ALLOW)));
              // TODO(b/138913138): not trivial; assigned multiple times, but within a certain
              // range.
              assertFalse(
                  mainMethod
                      .streamInstructions()
                      .noneMatch(i -> i.isConstString("Dead code: 6", JumboStringMode.ALLOW)));
              assertTrue(
                  mainMethod
                      .streamInstructions()
                      .noneMatch(i -> i.isConstString("Dead code: 7", JumboStringMode.ALLOW)));
              assertTrue(
                  mainMethod
                      .streamInstructions()
                      .noneMatch(i -> i.isConstString("Dead code: 8", JumboStringMode.ALLOW)));
            })
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("The end");
  }

  static class TestClass {
    public static void main(String... args) {
      if (StaticFieldWithoutInitialization_Z.alwaysFalse) {
        System.out.println("Dead code: 1");
      }
      StaticFieldWithInitialization_Z.not_clinit();
      if (StaticFieldWithInitialization_Z.alwaysFalse) {
        System.out.println("Dead code: 2");
      }
      StaticFieldWithNonTrivialInitialization_Z.not_clinit();
      if (StaticFieldWithNonTrivialInitialization_Z.alwaysFalse
          || !StaticFieldWithNonTrivialInitialization_Z.alwaysTrue) {
        System.out.println("Dead code: 3");
      }
      if (StaticFieldWithoutInitialization_I.alwaysZero != 0) {
        System.out.println("Dead code: 4");
      }
      if (StaticFieldWithInitialization_I.alwaysZero != 0) {
        System.out.println("Dead code: 5");
      }
      StaticFieldWithRange_I.foo();
      StaticFieldWithRange_I.bar();
      if (StaticFieldWithRange_I.alwaysLessThanEight >= 8) {
        System.out.println("Dead code: 6");
      }
      if (StaticFieldWithoutInitialization_L.alwaysNull != null) {
        System.out.println("Dead code: 7");
      }
      StaticFieldWithInitialization_L.not_clinit();
      if (StaticFieldWithInitialization_L.alwaysNull != null) {
        System.out.println("Dead code: 8");
      }
      System.out.println("The end");
    }
  }

  @NeverClassInline
  static class StaticFieldWithoutInitialization_Z {
    static boolean alwaysFalse;
  }

  @NeverClassInline
  @NoVerticalClassMerging
  static class StaticFieldWithInitialization_Z {
    static boolean alwaysFalse;
    @NeverInline
    static void not_clinit() {
      alwaysFalse = false;
    }
  }

  @NeverClassInline
  static class StaticFieldWithNonTrivialInitialization_Z
      extends StaticFieldWithInitialization_Z {
    static boolean alwaysTrue;
    @NeverInline
    static void not_clinit() {
      alwaysTrue = alwaysFalse;
      alwaysTrue = true;
    }
  }

  @NeverClassInline
  static class StaticFieldWithoutInitialization_I {
    static int alwaysZero;
  }

  @NeverClassInline
  static class StaticFieldWithInitialization_I {
    static int alwaysZero;
    static {
      alwaysZero = 0;
    }
  }

  @NeverClassInline
  static class StaticFieldWithRange_I {
    static int alwaysLessThanEight;

    @NeverInline
    static void foo() {
      alwaysLessThanEight = 4;
    }

    @NeverInline
    static void bar() {
      alwaysLessThanEight = 2;
    }
  }

  @NeverClassInline
  static class StaticFieldWithoutInitialization_L {
    static Object alwaysNull;
  }

  @NeverClassInline
  static class StaticFieldWithInitialization_L {
    static Object alwaysNull;
    @NeverInline
    static void not_clinit() {
      alwaysNull = null;
    }
  }
}
