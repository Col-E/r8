// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.AndroidApiLevel.L_MR1;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoInliningOfLambdaTest extends TestBase {

  private final TestParameters parameters;
  private final String EXPECTED =
      StringUtils.lines(
          "ApiCaller::getAction",
          "Api::apiLevel22",
          "ApiCaller::callApi",
          "Api::apiLevel22",
          "Action::getAction",
          "Api::apiLevel22");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelNoInliningOfLambdaTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue("b/197494749", parameters.canUseDefaultAndStaticInterfaceMethods());
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    testForR8(parameters.getBackend())
        .addProgramClasses(ApiCaller.class, Action.class, Main.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .apply(setMockApiLevelForMethod(apiMethod, L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .allowAccessModification()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject action;
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                action = inspector.clazz(Action.class);
              } else {
                action = inspector.companionClassFor(Action.class);
              }
              ClassSubject apiCaller = inspector.clazz(ApiCaller.class);
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(L_MR1)) {
                // Both Action and ApiCaller have been optimized out.
                assertThat(action, isAbsent());
                assertThat(apiCaller, isAbsent());
              } else {
                assertThat(action, isPresent());
                MethodSubject action$lambda$getAction$0 =
                    action.uniqueMethodWithOriginalName("lambda$getAction$0");
                assertThat(action$lambda$getAction$0, isPresent());
                assertThat(
                    action$lambda$getAction$0, CodeMatchers.invokesMethodWithName("apiLevel22"));

                assertThat(apiCaller, isPresent());
                MethodSubject callApi = apiCaller.uniqueMethodWithOriginalName("callApi");
                assertThat(callApi, isPresent());
                assertThat(callApi, CodeMatchers.invokesMethodWithName("apiLevel22"));
              }
            })
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public static class Api {

    public static void apiLevel22() {
      System.out.println("Api::apiLevel22");
    }
  }

  public interface Action {

    void doSomething();

    static Action getAction() {
      return () -> {
        System.out.println("Action::getAction");
        Api.apiLevel22();
      };
    }
  }

  public static class ApiCaller {

    public static Action getAction() {
      return () -> {
        System.out.println("ApiCaller::getAction");
        Api.apiLevel22();
      };
    }

    public static Action getActionMethodReference() {
      return ApiCaller::callApi;
    }

    public static void callApi() {
      System.out.println("ApiCaller::callApi");
      Api.apiLevel22();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ApiCaller.getAction().doSomething();
      ApiCaller.getActionMethodReference().doSomething();
      Action.getAction().doSomething();
    }
  }
}
