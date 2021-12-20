// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineNoMockingTest extends TestBase {

  private final AndroidApiLevel libraryApiLevel = AndroidApiLevel.M;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    assumeFalse(
        parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isEqualTo(Version.V12_0_0));
    boolean isMethodApiLevel =
        parameters.isDexRuntime()
            && parameters.getApiLevel().isGreaterThanOrEqualTo(libraryApiLevel);
    Method getterOn23 = LibraryClass.class.getDeclaredMethod("getterOn23");
    Method methodOn23 = LibraryClass.class.getDeclaredMethod("methodOn23");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, libraryApiLevel))
        .apply(setMockApiLevelForMethod(getterOn23, libraryApiLevel))
        .apply(setMockApiLevelForMethod(methodOn23, libraryApiLevel))
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::enableStubbingOfClasses)
        .compile()
        .applyIf(
            parameters.isDexRuntime()
                && parameters
                    .getRuntime()
                    .maxSupportedApiLevel()
                    .isGreaterThanOrEqualTo(libraryApiLevel),
            b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(!isMethodApiLevel, "Hello World")
        .assertSuccessWithOutputLinesIf(isMethodApiLevel, "LibraryClass::methodOn23", "Hello World")
        .inspect(
            inspector -> {
              Method mainMethod = Main.class.getDeclaredMethod("main", String[].class);
              assertThat(inspector.method(mainMethod), isPresent());
              verifyThat(parameters, getterOn23).isOutlinedFromUntil(mainMethod, libraryApiLevel);
              verifyThat(parameters, methodOn23).isOutlinedFromUntil(mainMethod, libraryApiLevel);
              assertThat(inspector.clazz(LibraryClass.class), isAbsent());
            });
  }

  // Only present from api level 19.
  public static class LibraryClass {

    public static LibraryClass getterOn23() {
      return new LibraryClass();
    }

    public void methodOn23() {
      System.out.println("LibraryClass::methodOn23");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        LibraryClass.getterOn23().methodOn23();
      }
      System.out.println("Hello World");
    }
  }
}
