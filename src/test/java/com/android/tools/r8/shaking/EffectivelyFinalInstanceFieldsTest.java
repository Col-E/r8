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
import com.android.tools.r8.NoHorizontalClassMerging;
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
public class EffectivelyFinalInstanceFieldsTest extends TestBase {

  private static final Class<?> MAIN = TestClass.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("The end");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EffectivelyFinalInstanceFieldsTest.class)
        .addKeepMainRule(MAIN)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
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
              assertTrue(
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
      InstanceFieldWithoutInitialization_Z i1 = new InstanceFieldWithoutInitialization_Z();
      if (i1.alwaysFalse) {
        System.out.println("Dead code: 1");
      }
      InstanceFieldWithInitialization_Z i2 = new InstanceFieldWithInitialization_Z();
      if (i2.alwaysFalse) {
        System.out.println("Dead code: 2");
      }
      InstanceFieldWithNonTrivialInitialization_Z i3 =
          new InstanceFieldWithNonTrivialInitialization_Z();
      if (i3.alwaysFalse || !i3.alwaysTrue) {
        System.out.println("Dead code: 3");
      }
      InstanceFieldWithoutInitialization_I i4 = new InstanceFieldWithoutInitialization_I();
      if (i4.alwaysZero != 0) {
        System.out.println("Dead code: 4");
      }
      InstanceFieldWithInitialization_I i5 = new InstanceFieldWithInitialization_I();
      if (i5.alwaysZero != 0) {
        System.out.println("Dead code: 5");
      }
      InstanceFieldWithRange_I i6 = new InstanceFieldWithRange_I();
      i6.foo();
      i6.bar();
      if (i6.alwaysLessThanEight >= 8) {
        System.out.println("Dead code: 6");
      }
      InstanceFieldWithoutInitialization_L i7 = new InstanceFieldWithoutInitialization_L();
      if (i7.alwaysNull != null) {
        System.out.println("Dead code: 7");
      }
      InstanceFieldWithInitialization_L i8 = new InstanceFieldWithInitialization_L();
      if (i8.alwaysNull != null) {
        System.out.println("Dead code: 8");
      }
      System.out.println("The end");
    }
  }

  @NeverClassInline
  static class InstanceFieldWithoutInitialization_Z {
    boolean alwaysFalse;

    InstanceFieldWithoutInitialization_Z() {
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  static class InstanceFieldWithInitialization_Z {
    boolean alwaysFalse;
    InstanceFieldWithInitialization_Z() {
      alwaysFalse = false;
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class InstanceFieldWithNonTrivialInitialization_Z
      extends InstanceFieldWithInitialization_Z {
    boolean alwaysTrue;
    InstanceFieldWithNonTrivialInitialization_Z() {
      super();
      alwaysTrue = alwaysFalse;
      alwaysTrue = true;
    }
  }

  @NeverClassInline
  static class InstanceFieldWithoutInitialization_I {
    int alwaysZero;

    InstanceFieldWithoutInitialization_I() {
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class InstanceFieldWithInitialization_I {
    int alwaysZero;
    InstanceFieldWithInitialization_I() {
      alwaysZero = 0;
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class InstanceFieldWithRange_I {
    int alwaysLessThanEight;
    InstanceFieldWithRange_I() {
    }

    @NeverInline
    void foo() {
      alwaysLessThanEight = 4;
    }

    @NeverInline
    void bar() {
      alwaysLessThanEight = 2;
    }
  }

  @NeverClassInline
  static class InstanceFieldWithoutInitialization_L {
    Object alwaysNull;

    InstanceFieldWithoutInitialization_L() {
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class InstanceFieldWithInitialization_L {
    Object alwaysNull;
    InstanceFieldWithInitialization_L() {
      alwaysNull = null;
    }
  }
}
