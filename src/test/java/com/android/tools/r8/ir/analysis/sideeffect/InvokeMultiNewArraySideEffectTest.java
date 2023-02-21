// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.sideeffect;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeMultiNewArraySideEffectTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeMultiNewArraySideEffectTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeMultiNewArraySideEffectTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              if (parameters.isCfRuntime()) {
                assertThat(aClassSubject, not(isPresent()));
              } else {
                // TODO(b/138779026): If we allow InvokeMultiNewArray instructions in the IR when
                //  compiling to DEX, A would become dead.
                assertThat(aClassSubject, isPresent());
              }

              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatMatches(
            containsString(NegativeArraySizeException.class.getSimpleName()));
  }

  static class TestClass {

    public static void main(String[] args) {
      A[][] a = new A[42][42];
      B[][] b = new B[42][-1];
    }
  }

  @NoHorizontalClassMerging
  static class A {}

  @NoHorizontalClassMerging
  static class B {}
}
