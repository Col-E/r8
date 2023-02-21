// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;

import com.android.tools.r8.NeverInline;
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
public class ApiModelInlineInSameClassDifferentApiLevelTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelInlineInSameClassDifferentApiLevelTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method apiLevel22 = Api.class.getDeclaredMethod("apiLevel22");
    Method callApi = ApiCaller.class.getDeclaredMethod("callApi");
    Method callCallApi = ApiCaller.class.getDeclaredMethod("callCallApi");
    testForR8(parameters.getBackend())
        .addProgramClasses(ApiCaller.class, Main.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(setMockApiLevelForMethod(apiLevel22, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .enableInliningAnnotations()
        .compile()
        .inspect(inspector -> verifyThat(inspector, parameters, callApi).inlinedInto(callCallApi))
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Api::apiLevel22");
  }

  public static class Api {

    public static void apiLevel22() {
      System.out.println("Api::apiLevel22");
    }
  }

  public static class ApiCaller {

    public static void callApi() {
      Api.apiLevel22();
    }

    @NeverInline
    public static void callCallApi() {
      callApi();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ApiCaller.callCallApi();
    }
  }
}
