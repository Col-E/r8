// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.AndroidApiLevel.L_MR1;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

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
public class ApiModelNoInliningOfStaticInterfaceMethodsTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelNoInliningOfStaticInterfaceMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    // Method apiCaller = ApiCaller.class.getDeclaredMethod("callApiLevel");
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
              // The call to the api is moved to $-CC (or stays) and is then merged if allowed.
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(L_MR1)) {
                assertEquals(1, inspector.allClasses().size());
              } else {
                assertEquals(2, inspector.allClasses().size());
                ClassSubject aSubject = inspector.clazz(A.class);
                assertThat(aSubject, isPresent());
                // TODO(b/191008231): Should not invoke api here but stay on the CC class.
                assertEquals(1, aSubject.allMethods().size());
                MethodSubject callApiLevel = aSubject.uniqueMethodWithName("callApiLevel");
                assertThat(callApiLevel, isPresent());
                assertThat(callApiLevel, CodeMatchers.invokesMethodWithName("apiLevel22"));
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
    static void callApiLevel() {
      System.out.println("ApiCaller::callApiLevel");
      Api.apiLevel22();
    }
  }

  public static class A implements ApiCaller {

    public static void noApiCall() {
      System.out.println("A::noApiCall");
      ApiCaller.callApiLevel();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.noApiCall();
    }
  }
}
