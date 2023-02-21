// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;

import com.android.tools.r8.NeverInline;
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
public class ApiModelClassMergingWithDifferentApiMethodsTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"Api::apiLevel22", "B::bar"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelClassMergingWithDifferentApiMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method apiMethod = Api.class.getDeclaredMethod("apiLevel22");
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, Main.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L_MR1)) {
                inspector.assertClassesMerged(A.class, B.class);
              } else {
                inspector.assertNoClassesMerged();
              }
            })
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .apply(setMockApiLevelForMethod(apiMethod, AndroidApiLevel.L_MR1))
        .compile()
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Api {

    public static void apiLevel22() {
      System.out.println("Api::apiLevel22");
    }
  }

  public static class A {

    @NeverInline
    public static void callFoo() {
      Api.apiLevel22();
    }
  }

  public static class B {

    @NeverInline
    public static void bar() {
      System.out.println("B::bar");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.callFoo();
      B.bar();
    }
  }
}
