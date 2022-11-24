// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelInlineMethodWithApiTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelInlineMethodWithApiTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method apiCallerApiLevel22 = ApiCaller.class.getDeclaredMethod("apiLevel22");
    testForR8(parameters.getBackend())
        .addProgramClasses(ApiCaller.class, ApiCallerCaller.class, OtherCaller.class, Main.class)
        .addLibraryClasses(ApiType.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .enableNoHorizontalClassMergingAnnotations()
        .apply(setMockApiLevelForClass(ApiType.class, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .compile()
        .addRunClasspathClasses(ApiType.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(ApiType.class.getName())
        .inspect(
            inspector -> {
              assertThat(
                  inspector.method(apiCallerApiLevel22),
                  notIf(
                      isPresent(),
                      parameters.isDexRuntime()
                          && parameters
                              .getApiLevel()
                              .isGreaterThanOrEqualTo(AndroidApiLevel.L_MR1)));
              assertThat(inspector.clazz(OtherCaller.class), not(isPresent()));
            });
  }

  public static class ApiType {}

  @NoHorizontalClassMerging
  public static class ApiCaller {

    public static ApiType apiLevel22() throws Exception {
      // The reflective call here is to ensure that the setting of A's api level is not based on
      // a method reference to `Api` and only because of the type reference in the checkcast.
      Class<?> reflectiveCall =
          Class.forName(
              "com.android.tools.r8.apimodel.ApiModelInlineMethodWithApiTypeTest_ApiType"
                  .replace("_", "$"));
      return (ApiType) reflectiveCall.getDeclaredConstructor().newInstance();
    }
  }

  @NoHorizontalClassMerging
  public static class ApiCallerCaller {

    public static void apiLevel22() throws Exception {
      // This is referencing the proto of ApiCaller.foo and thus have a reference to ApiType. It is
      // therefore OK to inline ApiCaller.apiLevel22() into ApiCallerCaller.apiLevel22().
      System.out.println(ApiCaller.apiLevel22().getClass().getName());
    }
  }

  @NoHorizontalClassMerging
  public static class OtherCaller {

    public static void apiLevel1() throws Exception {
      // ApiCallerCaller.apiLevel22 should never be inlined here.
      ApiCallerCaller.apiLevel22();
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      OtherCaller.apiLevel1();
    }
  }
}
