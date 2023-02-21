// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.addTracedApiReferenceLevelCallBack;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelFieldTypeReferenceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelFieldTypeReferenceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method readApiField = B.class.getDeclaredMethod("readApiField");
    Method setApiField = B.class.getDeclaredMethod("setApiField", Object.class);
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, Main.class)
        .addLibraryClasses(Api.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(setMockApiLevelForClass(Api.class, AndroidApiLevel.L_MR1))
        .apply(setMockApiLevelForDefaultInstanceInitializer(Api.class, AndroidApiLevel.L_MR1))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .apply(
            addTracedApiReferenceLevelCallBack(
                (method, apiLevel) -> {
                  if (Reference.methodFromMethod(readApiField).equals(method)) {
                    if (parameters.isCfRuntime()) {
                      assertEquals(AndroidApiLevel.B, apiLevel);
                    } else {
                      assertEquals(AndroidApiLevel.B.max(parameters.getApiLevel()), apiLevel);
                    }
                  }
                  if (Reference.methodFromMethod(setApiField).equals(method)) {
                    if (parameters.isCfRuntime()) {
                      assertEquals(AndroidApiLevel.L_MR1, apiLevel);
                    } else {
                      assertEquals(AndroidApiLevel.L_MR1.max(parameters.getApiLevel()), apiLevel);
                    }
                  }
                }))
        .compile()
        .addRunClasspathClasses(Api.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Api");
  }

  public static class Api {}

  public static class A {

    public static Api api;
  }

  public static class B {

    public static void readApiField() {
      System.out.println(A.api == null ? "null" : "Api");
    }

    public static void setApiField(Object obj) {
      A.api = (Api) obj;
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (args.length > 0) {
        B.setApiField(args[0]);
      } else {
        B.setApiField(new Api());
      }
      B.readApiField();
    }
  }
}
