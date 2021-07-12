// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.AndroidApiLevel.L_MR1;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoInliningOfDefaultInterfaceMethodsTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelNoInliningOfDefaultInterfaceMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test()
  public void testR8() throws Exception {
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, ApiCaller.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .apply(setMockApiLevelForMethod(apiMethod, L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .noMinification()
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(Main.class), isPresent());
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(L_MR1)) {
                assertEquals(1, inspector.allClasses().size());
              } else {
                ClassSubject aSubject = inspector.clazz(A.class);
                ClassSubject apiCaller = inspector.clazz(ApiCaller.class);
                assertThat(apiCaller, isPresent());
                MethodSubject callApiLevel = apiCaller.uniqueMethodWithName("callApiLevel");
                if (parameters.isCfRuntime()) {
                  assertThat(aSubject, isPresent());
                  assertThat(callApiLevel, CodeMatchers.invokesMethodWithName("apiLevel22"));
                } else {
                  assert parameters.isDexRuntime();
                  // TODO(b/191013385): A has a virtual method that calls callApiLevel on $CC, but
                  //  that call should be inlined.
                  assertThat(aSubject, isPresent());
                  assertThat(callApiLevel, isAbstract());
                  ClassSubject classSubject = apiCaller.toCompanionClass();
                  assertThat(classSubject, isPresent());
                  assertEquals(1, classSubject.allMethods().size());
                  assertThat(
                      classSubject.allMethods().get(0),
                      CodeMatchers.invokesMethodWithName("apiLevel22"));
                }
              }
            })
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::noApiCall", "ApiCaller::callApiLevel", "Api::apiLevel22");
  }

  public static class Api {

    public static void apiLevel22() {
      System.out.println("Api::apiLevel22");
    }
  }

  public interface ApiCaller {
    default void callApiLevel() {
      System.out.println("ApiCaller::callApiLevel");
      Api.apiLevel22();
    }
  }

  public static class A implements ApiCaller {

    public void noApiCall() {
      System.out.println("A::noApiCall");
      callApiLevel();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().noApiCall();
    }
  }
}
