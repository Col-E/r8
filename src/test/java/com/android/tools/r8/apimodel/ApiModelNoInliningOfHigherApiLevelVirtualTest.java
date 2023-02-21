// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelNoInliningOfHigherApiLevelVirtualTest.ApiCaller.callVirtualMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
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
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoInliningOfHigherApiLevelVirtualTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    Method apiCaller = ApiCaller.class.getDeclaredMethod("callVirtualMethod");
    Method apiCallerCaller = A.class.getDeclaredMethod("noApiCall");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, ApiCaller.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .apply(setMockApiLevelForMethod(apiMethod, AndroidApiLevel.L_MR1))
        .apply(setMockApiLevelForClass(Api.class, AndroidApiLevel.L_MR1))
        .apply(setMockApiLevelForDefaultInstanceInitializer(Api.class, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .compile()
        .inspect(
            inspector ->
                verifyThat(inspector, parameters, apiCaller)
                    .inlinedIntoFromApiLevel(apiCallerCaller, AndroidApiLevel.L_MR1))
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "A::noApiCall", "ApiCaller::callVirtualMethod", "Api::apiLevel22");
  }

  public static class Api {

    public void apiLevel22() {
      System.out.println("Api::apiLevel22");
    }
  }

  @NoHorizontalClassMerging
  public static class ApiCaller {

    public static void callVirtualMethod() {
      System.out.println("ApiCaller::callVirtualMethod");
      new Api().apiLevel22();
    }
  }

  @NoHorizontalClassMerging
  public static class A {

    @NeverInline
    public static void noApiCall() {
      System.out.println("A::noApiCall");
      callVirtualMethod();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.noApiCall();
    }
  }
}
