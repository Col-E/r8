// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForField;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.AndroidApiLevel.L_MR1;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoInliningOfHigherApiLevelInstanceFieldTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelNoInliningOfHigherApiLevelInstanceFieldTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Field apiField = Api.class.getDeclaredField("apiLevel22");
    Method apiCaller = ApiCaller.class.getDeclaredMethod("getInstanceField");
    Method apiCallerCaller = ApiCallerCaller.class.getDeclaredMethod("noApiCall");
    testForR8(parameters.getBackend())
        .addProgramClasses(ApiCaller.class, ApiCallerCaller.class, Main.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .apply(setMockApiLevelForField(apiField, L_MR1))
        .apply(setMockApiLevelForClass(Api.class, L_MR1))
        .apply(setMockApiLevelForDefaultInstanceInitializer(Api.class, L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .compile()
        .inspect(
            inspector ->
                verifyThat(inspector, parameters, apiCaller)
                    .inlinedIntoFromApiLevel(apiCallerCaller, L_MR1))
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  // The api class does not have an api level to ensure it is not the instance initializer that is
  // keeping us from inlining.
  public static class Api {

    public String apiLevel22 = "Hello World!";
  }

  @NoHorizontalClassMerging
  public static class ApiCaller {

    public static void getInstanceField() {
      System.out.println(new Api().apiLevel22);
    }
  }

  @NoHorizontalClassMerging
  public static class ApiCallerCaller {

    @NeverInline
    public static void noApiCall() {
      ApiCaller.getInstanceField();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ApiCallerCaller.noApiCall();
    }
  }
}
