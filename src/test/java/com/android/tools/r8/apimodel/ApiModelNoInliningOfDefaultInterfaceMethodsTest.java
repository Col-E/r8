// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.AndroidApiLevel.L_MR1;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoMethodStaticizing;
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
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(setMockApiLevelForMethod(apiMethod, L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .enableNoMethodStaticizingAnnotations()
        .addDontObfuscate()
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
                if (parameters.isCfRuntime()) {
                  assert parameters.canUseDefaultAndStaticInterfaceMethods();
                  assertThat(apiCaller, isPresent());
                  assertThat(aSubject, isPresent());
                  MethodSubject callApiLevel =
                      apiCaller.uniqueMethodWithOriginalName("callApiLevel");
                  assertThat(callApiLevel, CodeMatchers.invokesMethodWithName("apiLevel22"));
                } else {
                  assert parameters.isDexRuntime();
                  assert !parameters.canUseDefaultAndStaticInterfaceMethods();
                  assertThat(apiCaller, isAbsent());
                  assertThat(aSubject, isAbsent());
                  ClassSubject companionClass = apiCaller.toCompanionClass();
                  assertThat(companionClass, isPresent());
                  assertEquals(1, companionClass.allMethods().size());
                  assertThat(
                      companionClass.allMethods().get(0),
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

    @NoMethodStaticizing
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
