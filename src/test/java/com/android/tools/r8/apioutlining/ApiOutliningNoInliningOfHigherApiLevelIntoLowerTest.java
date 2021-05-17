// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apioutlining;

import static com.android.tools.r8.apioutlining.ApiOutliningTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apioutlining.ApiOutliningTestHelper.verifyThat;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
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
public class ApiOutliningNoInliningOfHigherApiLevelIntoLowerTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public ApiOutliningNoInliningOfHigherApiLevelIntoLowerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test()
  public void testR8() throws Exception {
    Method apiLevel21 = A.class.getDeclaredMethod("apiLevel21");
    Method apiLevel22 = B.class.getDeclaredMethod("apiLevel22");
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(Main.class)
            .enableInliningAnnotations()
            .apply(setMockApiLevelForMethod(apiLevel21, AndroidApiLevel.L))
            .apply(setMockApiLevelForMethod(apiLevel22, AndroidApiLevel.L_MR1))
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines("A::apiLevel21", "B::apiLevel22");
    if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L_MR1)) {
      runResult.inspect(
          verifyThat(parameters, apiLevel22)
              .inlinedIntoFromApiLevel(apiLevel21, AndroidApiLevel.L_MR1));
    } else {
      // TODO(b/188388130): Should only inline on minApi >= 22.
      assertThrows(
          AssertionError.class,
          () ->
              runResult.inspect(
                  verifyThat(parameters, apiLevel22)
                      .inlinedIntoFromApiLevel(apiLevel21, AndroidApiLevel.L_MR1)));
    }
  }

  public static class B {
    public static void apiLevel22() {
      System.out.println("B::apiLevel22");
    }
  }

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
