// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoInliningOfTryCatchReferenceTest extends TestBase {

  private final AndroidApiLevel exceptionApiLevel = AndroidApiLevel.L_MR1;

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    Method tryCatch = TestClass.class.getDeclaredMethod("testTryCatch");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, TestClass.class, Caller.class, KeptClass.class)
        .addLibraryClasses(ApiException.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMethodRules(
            Reference.methodFromMethod(KeptClass.class.getDeclaredMethod("keptMethodThatMayThrow")))
        .addKeepMainRule(Main.class)
        .apply(setMockApiLevelForClass(ApiException.class, exceptionApiLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(ApiException.class, exceptionApiLevel))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .enableInliningAnnotations()
        .addHorizontallyMergedClassesInspector(
            horizontallyMergedClassesInspector -> {
              if (parameters.isDexRuntime()
                  && parameters.getApiLevel().isGreaterThanOrEqualTo(exceptionApiLevel)) {
                horizontallyMergedClassesInspector.assertClassesMerged(
                    TestClass.class, Caller.class);
              } else {
                horizontallyMergedClassesInspector.assertNoClassesMerged();
              }
            })
        .apply(
            ApiModelingTestHelper.addTracedApiReferenceLevelCallBack(
                (reference, apiLevel) -> {
                  if (reference.equals(Reference.methodFromMethod(tryCatch))) {
                    assertEquals(
                        exceptionApiLevel.max(
                            parameters.isCfRuntime()
                                ? AndroidApiLevel.B
                                : parameters.getApiLevel()),
                        apiLevel);
                  }
                }))
        .compile()
        .applyIf(
            parameters.isCfRuntime()
                || parameters
                    .asDexRuntime()
                    .getMinApiLevel()
                    .isGreaterThanOrEqualTo(exceptionApiLevel),
            b -> b.addRunClasspathClasses(ApiException.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World");
  }

  public static class ApiException extends RuntimeException {}

  public static class TestClass {

    public static void testTryCatch() {
      try {
        KeptClass.keptMethodThatMayThrow();
      } catch (ApiException e) {
        System.out.println("Caught ApiException");
      }
    }
  }

  public static class KeptClass {

    public static void keptMethodThatMayThrow() {
      if (System.currentTimeMillis() == 0) {
        throw new ApiException();
      }
      System.out.println("Hello World");
    }
  }

  public static class Caller {

    @NeverInline
    public static void callTestClass() {
      TestClass.testTryCatch();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Caller.callTestClass();
    }
  }
}
