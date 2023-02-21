// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.AndroidApiLevel.L_MR1;
import static com.android.tools.r8.utils.AndroidApiLevel.O;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
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
    Method apiMethod22 = Api.class.getDeclaredMethod("apiLevel22");
    Method apiMethod26 = Api.class.getDeclaredMethod("apiLevel26");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, ApiCaller.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(setMockApiLevelForMethod(apiMethod22, L_MR1))
        .apply(setMockApiLevelForMethod(apiMethod26, O))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .addDontObfuscate()
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              // The call to the api is moved to $-CC (or stays) and is then merged if allowed.
              Method callApiLevel22 = ApiCaller.class.getDeclaredMethod("callApiLevel22");
              Method callApiLevel26 = ApiCaller.class.getDeclaredMethod("callApiLevel26");
              Method noApiCallTo22 = A.class.getDeclaredMethod("noApiCallTo22");
              Method noApiCallTo26 = A.class.getDeclaredMethod("noApiCallTo26");
              if (!parameters.canUseDefaultAndStaticInterfaceMethods()) {
                ClassSubject companion = inspector.companionClassFor(ApiCaller.class);
                assertThat(companion, isPresent());
                FoundClassSubject foundCompanion = companion.asFoundClassSubject();
                verifyThat(inspector, parameters, callApiLevel22)
                    .setHolder(foundCompanion)
                    .inlinedIntoFromApiLevel(noApiCallTo22, L_MR1);
                verifyThat(inspector, parameters, callApiLevel26)
                    .setHolder(foundCompanion)
                    .inlinedIntoFromApiLevel(noApiCallTo26, O);
              } else {
                verifyThat(inspector, parameters, callApiLevel22)
                    .inlinedIntoFromApiLevel(noApiCallTo22, L_MR1);
                verifyThat(inspector, parameters, callApiLevel26)
                    .inlinedIntoFromApiLevel(noApiCallTo26, O);
              }
            })
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "A::noApiCallTo22",
            "ApiCaller::callApiLevel22",
            "Api::apiLevel22",
            "A::noApiCallTo26",
            "ApiCaller::callApiLevel26",
            "Api::apiLevel26");
  }

  public static class Api {

    public static void apiLevel22() {
      System.out.println("Api::apiLevel22");
    }

    public static void apiLevel26() {
      System.out.println("Api::apiLevel26");
    }
  }

  public interface ApiCaller {
    static void callApiLevel22() {
      System.out.println("ApiCaller::callApiLevel22");
      Api.apiLevel22();
    }

    static void callApiLevel26() {
      System.out.println("ApiCaller::callApiLevel26");
      Api.apiLevel26();
    }
  }

  public static class A {

    @NeverInline
    public static void noApiCallTo22() {
      System.out.println("A::noApiCallTo22");
      ApiCaller.callApiLevel22();
    }

    @NeverInline
    public static void noApiCallTo26() {
      System.out.println("A::noApiCallTo26");
      ApiCaller.callApiLevel26();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.noApiCallTo22();
      A.noApiCallTo26();
    }
  }
}
