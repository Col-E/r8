// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoInliningOfHigherApiLevelIntoLowerDirectTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelNoInliningOfHigherApiLevelIntoLowerDirectTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test()
  public void testR8() throws Exception {
    Method apiLevel21 = A.class.getDeclaredMethod("apiLevel21");
    Method apiLevel22 = B.class.getDeclaredMethod("apiLevel22");
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .apply(setMockApiLevelForMethod(apiLevel21, AndroidApiLevel.L))
        .apply(setMockApiLevelForMethod(apiLevel22, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::apiLevel21", "B::apiLevel22")
        .inspect(
            inspector -> verifyThat(inspector, parameters, apiLevel22).inlinedInto(apiLevel21));
  }

  // This tests that program classes where we directly mock the methods to have an api level will
  // be inlined.
  @NoHorizontalClassMerging
  public static class B {
    public static void apiLevel22() {
      System.out.println("B::apiLevel22");
    }
  }

  @NoHorizontalClassMerging
  public static class A {

    @NeverInline
    public static void apiLevel21() {
      System.out.println("A::apiLevel21");
      B.apiLevel22();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.apiLevel21();
    }
  }
}
