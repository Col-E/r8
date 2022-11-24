// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelTypeReferenceInvokeTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    Method apiMethod = LibraryClass.class.getDeclaredMethod("apiMethod");
    boolean libraryClassOnBoot =
        parameters.isDexRuntime()
            && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.M);
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, ApiHelper.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .apply(setMockApiLevelForClass(LibraryClass.class, AndroidApiLevel.M))
        .apply(setMockApiLevelForMethod(apiMethod, AndroidApiLevel.M))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addAndroidBuildVersion()
        .compile()
        .inspect(inspector -> assertThat(inspector.clazz(ApiHelper.class), isAbsent()))
        .addRunClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(
            libraryClassOnBoot, "LibraryClass::apiMethod", "Hello World")
        .assertSuccessWithOutputLinesIf(!libraryClassOnBoot, "Hello World");
  }

  public static class LibraryClass {

    public void apiMethod() {
      System.out.println("LibraryClass::apiMethod");
    }
  }

  public static class ApiHelper {

    public static void apiCaller(LibraryClass libraryClass) {
      if (AndroidBuildVersion.VERSION >= 23) {
        libraryClass.apiMethod();
      }
    }
  }

  public static class Main {

    @NeverInline
    public static void typeReference(Object object) {
      if (object instanceof LibraryClass) {
        ApiHelper.apiCaller((LibraryClass) object);
      }
    }

    public static void main(String[] args) {
      typeReference(args.length == 0 ? new LibraryClass() : "Foo");
      System.out.println("Hello World");
    }
  }
}
