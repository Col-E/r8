// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.AndroidApiLevel.L_MR1;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelInlineInSameClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelInlineInSameClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    Method callingApi = ApiCaller.class.getDeclaredMethod("callingApi");
    Method notCallingApi = ApiCaller.class.getDeclaredMethod("notCallingApi");
    Method main = Main.class.getDeclaredMethod("main", String[].class);
    testForR8(parameters.getBackend())
        .addProgramClasses(ApiCaller.class, ApiCallerCaller.class, Main.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(setMockApiLevelForMethod(apiMethod, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .compile()
        .inspect(
            inspector -> {
              // No matter the api level, we should always inline callingApi into notCallingApi.
              verifyThat(inspector, parameters, notCallingApi).inlinedIntoFromApiLevel(main, L_MR1);
              assertThat(inspector.method(callingApi), not(isPresent()));
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(L_MR1)) {
                ClassSubject mainSubject = inspector.clazz(Main.class);
                MethodSubject mainMethodSubject = mainSubject.uniqueMethodWithOriginalName("main");
                assertThat(mainMethodSubject, isPresent());
                assertThat(mainMethodSubject, CodeMatchers.invokesMethodWithName("apiLevel22"));
              }
            })
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

    public static void callingApi() {
      Api.apiLevel22();
    }

    public static void notCallingApi() {
      // If api level information is not propagated correctly, inlining `callingApi` into
      // `notCallingApi` will have an api call that is not modeled correctly and it could be inlined
      // into its callers incorrectly.
      callingApi();
    }
  }

  public static class ApiCallerCaller {

    public static void foo() {
      ApiCaller.notCallingApi();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ApiCallerCaller.foo();
    }
  }
}
