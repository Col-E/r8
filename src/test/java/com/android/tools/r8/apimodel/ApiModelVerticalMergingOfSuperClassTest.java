// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.addTracedApiReferenceLevelCallBack;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.utils.AndroidApiLevel.L_MR1;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelVerticalMergingOfSuperClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelVerticalMergingOfSuperClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, Main.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .apply(setMockApiLevelForClass(Api.class, AndroidApiLevel.L_MR1))
        .apply(setMockApiLevelForDefaultInstanceInitializer(Api.class, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .apply(
            addTracedApiReferenceLevelCallBack(
                (methodReference, apiLevel) -> {
                  if (methodReference.getMethodName().equals("<init>")
                      && methodReference
                          .getHolderClass()
                          .equals(Reference.classFromClass(Api.class))) {
                    assertEquals(AndroidApiLevel.L_MR1, apiLevel);
                  }
                }))
        .addVerticallyMergedClassesInspector(
            inspector -> {
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(L_MR1)) {
                inspector.assertMergedIntoSubtype(A.class);
              } else {
                inspector.assertNoClassesMerged();
              }
            })
        .compile()
        .inspect(
            inspector -> {
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L_MR1)) {
                assertThat(inspector.clazz(A.class), not(isPresent()));
              } else {
                assertThat(inspector.clazz(A.class), isPresent());
              }
            })
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  public static class Api {}

  public static class A extends Api {

    @NeverInline
    public void bar() {
      System.out.println("Hello World!");
    }
  }

  @NeverClassInline
  public static class B extends A {

    public void foo() {
      bar();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B().foo();
    }
  }
}
