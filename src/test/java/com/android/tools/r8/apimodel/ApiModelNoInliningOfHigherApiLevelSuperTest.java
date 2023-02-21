// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.addTracedApiReferenceLevelCallBack;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoInliningOfHigherApiLevelSuperTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    Method apiCaller = ApiCaller.class.getDeclaredMethod("apiLevel22");
    Method apiCallerCaller = A.class.getDeclaredMethod("noApiCall");
    testForR8(parameters.getBackend())
        .addProgramClasses(ApiCaller.class, A.class, Main.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .apply(setMockApiLevelForMethod(apiMethod, AndroidApiLevel.L_MR1))
        .apply(setMockApiLevelForDefaultInstanceInitializer(Api.class, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .apply(
            addTracedApiReferenceLevelCallBack(
                (method, apiLevel) -> {
                  if (Reference.methodFromMethod(apiCaller).equals(method)) {
                    if (parameters.isCfRuntime()) {
                      assertEquals(AndroidApiLevel.L_MR1, apiLevel);
                    } else {
                      assertEquals(AndroidApiLevel.L_MR1.max(parameters.getApiLevel()), apiLevel);
                    }
                  }
                }))
        .compile()
        // We do not inline overrides calling super.
        .inspect(
            inspector ->
                verifyThat(inspector, parameters, apiCaller).notInlinedInto(apiCallerCaller))
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::noApiCall", "ApiCaller::apiLevel22", "Api::apiLevel22");
  }

  public static class Api {

    void apiLevel22() {
      System.out.println("Api::apiLevel22");
    }
  }

  @NeverClassInline
  public static class ApiCaller extends Api {

    @Override
    void apiLevel22() {
      System.out.println("ApiCaller::apiLevel22");
      super.apiLevel22();
    }
  }

  @NoHorizontalClassMerging
  public static class A {

    @NeverInline
    public static void noApiCall() {
      System.out.println("A::noApiCall");
      new ApiCaller().apiLevel22();
    }
  }

  @NoHorizontalClassMerging
  public static class Main {

    public static void main(String[] args) {
      A.noApiCall();
    }
  }
}
