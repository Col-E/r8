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
public class ApiModelNoInliningOfHigherApiLevelInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public ApiModelNoInliningOfHigherApiLevelInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    Method apiCaller = ApiCaller.class.getDeclaredMethod("callInterfaceMethod", Api.class);
    Method apiCallerCaller = A.class.getDeclaredMethod("noApiCall");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, ApiCaller.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .apply(setMockApiLevelForMethod(apiMethod, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::noApiCall", "ApiCaller::callInterfaceMethod")
        .inspect(
            verifyThat(parameters, apiCaller)
                .inlinedIntoFromApiLevel(apiCallerCaller, AndroidApiLevel.L_MR1));
  }

  public interface Api {

    void apiLevel22();
  }

  @NoHorizontalClassMerging
  public static class ApiCaller {

    public static void callInterfaceMethod(Api api) {
      System.out.println("ApiCaller::callInterfaceMethod");
      if (api != null) {
        api.apiLevel22();
      }
    }
  }

  @NoHorizontalClassMerging
  public static class A {

    @NeverInline
    public static void noApiCall() {
      System.out.println("A::noApiCall");
      ApiCaller.callInterfaceMethod(null);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.noApiCall();
    }
  }
}
