// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForField;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoClassInliningFieldTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelNoClassInliningFieldTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Field apiField = Api.class.getDeclaredField("foo");
    testForR8(parameters.getBackend())
        .addProgramClasses(ApiCallerCaller.class, ApiBuilder.class, Main.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .apply(setMockApiLevelForField(apiField, AndroidApiLevel.L_MR1))
        .apply(setMockApiLevelForClass(Api.class, AndroidApiLevel.L_MR1))
        .apply(setMockApiLevelForDefaultInstanceInitializer(Api.class, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .compile()
        .inspect(
            inspector -> {
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L_MR1)) {
                assertThat(inspector.clazz(ApiBuilder.class), not(isPresent()));
              } else {
                assertThat(inspector.clazz(ApiBuilder.class), isPresent());
              }
            })
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Api::apiLevel22");
  }

  public static class Api {

    public String foo = "Api::apiLevel22";
  }

  public static class ApiBuilder {

    public Api api;

    public ApiBuilder() {
      api = new Api();
    }

    public String build() {
      return api.foo;
    }
  }

  @NoHorizontalClassMerging
  public static class ApiCallerCaller {

    @NeverInline
    public static void callCallApi() {
      System.out.println(new ApiBuilder().build());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ApiCallerCaller.callCallApi();
    }
  }
}
