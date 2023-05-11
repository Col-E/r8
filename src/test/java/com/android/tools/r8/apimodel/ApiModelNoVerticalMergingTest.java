// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.AndroidApiLevel.L_MR1;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoInliningOfDefaultInitializer;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoVerticalMergingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelNoVerticalMergingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test()
  public void testR8() throws Exception {
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, Base.class, Sub.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(setMockApiLevelForMethod(apiMethod, L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoInliningOfDefaultInitializerAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .addVerticallyMergedClassesInspector(
            inspector -> {
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(L_MR1)) {
                inspector.assertMergedIntoSubtype(Base.class);
              } else {
                inspector.assertNoClassesMerged();
              }
            })
        .compile()
        .inspect(
            inspector -> {
              ClassSubject base = inspector.clazz(Base.class);
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(L_MR1)) {
                assertThat(base, not(isPresent()));
                ClassSubject sub = inspector.clazz(Sub.class);
                assertThat(sub, isPresent());
                assertThat(
                    sub.uniqueInstanceInitializer(),
                    isAbsentIf(parameters.canHaveNonReboundConstructorInvoke()));
                assertEquals(1, sub.virtualMethods().size());
                FoundMethodSubject callCallApi = sub.virtualMethods().get(0);
                assertEquals("callCallApi", callCallApi.getOriginalName());
                assertThat(callCallApi, CodeMatchers.invokesMethodWithName("apiLevel22"));
              } else {
                assertThat(base, isPresent());
              }
            })
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Sub::callCallApi", "Base::callApi", "Api::apiLevel22");
  }

  public static class Api {

    public static void apiLevel22() {
      System.out.println("Api::apiLevel22");
    }
  }

  public static class Base {

    public void callApi() {
      System.out.println("Base::callApi");
      Api.apiLevel22();
    }
  }

  @NeverClassInline
  @NoInliningOfDefaultInitializer
  public static class Sub extends Base {

    @NeverInline
    @NoMethodStaticizing
    public void callCallApi() {
      System.out.println("Sub::callCallApi");
      callApi();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new Sub().callCallApi();
    }
  }
}
