// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UpwardsArgumentPropagationToResolvedMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        // TODO(b/173398086): uniqueMethodWithName() does not work with argument removal.
        .addDontObfuscate()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              MethodSubject aMethodSubject = aClassSubject.uniqueMethodWithOriginalName("m");
              assertThat(aMethodSubject, isPresent());
              assertEquals(0, aMethodSubject.getProgramMethod().getReference().getArity());
              assertTrue(
                  aMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstNumber(-1)));

              ClassSubject aSub1ClassSubject = inspector.clazz(ASub1.class);
              assertThat(aSub1ClassSubject, isPresent());
              MethodSubject aSub1MethodSubject =
                  aSub1ClassSubject.uniqueMethodWithOriginalName("m");
              assertThat(aSub1MethodSubject, isPresent());
              assertEquals(0, aSub1MethodSubject.getProgramMethod().getReference().getArity());
              assertTrue(
                  aSub1MethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstNumber(42)));

              ClassSubject aSub2Sub1ClassSubject = inspector.clazz(ASub2Sub1.class);
              assertThat(aSub2Sub1ClassSubject, isPresent());
              MethodSubject aSub2Sub1MethodSubject =
                  aSub2Sub1ClassSubject.uniqueMethodWithOriginalName("m");
              assertThat(aSub2Sub1MethodSubject, isPresent());
              assertEquals(0, aSub2Sub1MethodSubject.getProgramMethod().getReference().getArity());
              assertTrue(
                  aSub2Sub1MethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstNumber(-1)));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "-1");
  }

  static class Main {

    public static void main(String[] args) {
      new ASub1().m(42);

      // During the top-down traversal over the class hierarchy in the argument propagator, the
      // following piece of argument information becomes active at ASub2 (due to the upper bound
      // type of the receiver being ASub2). Since ASub2 does not declare method m() itself, the
      // argument information is propagated upwards to A.m(). It is important that this piece of
      // information is not considered in any subsequent downwards propagation from A.m(), since
      // this leads to imprecision and nondeterminism. Specifically, if we later process ASub1, we
      // should not propagate the fact that x could be -1 to ASub1.m().
      (System.currentTimeMillis() > 0 ? new ASub2Sub1() : new ASub2Sub2()).m(-1);
    }
  }

  abstract static class A {

    public void m(int x) {
      System.out.println(x);
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class ASub1 extends A {

    @NeverInline
    @Override
    public void m(int x) {
      System.out.println(x);
    }
  }

  @NoHorizontalClassMerging
  abstract static class ASub2 extends A {}

  @NoHorizontalClassMerging
  static class ASub2Sub1 extends ASub2 {

    @NeverInline
    @Override
    public void m(int x) {
      System.out.println(x);
    }
  }

  @NoHorizontalClassMerging
  static class ASub2Sub2 extends ASub2 {}
}
