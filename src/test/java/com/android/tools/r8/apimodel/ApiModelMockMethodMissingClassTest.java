// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.Matchers;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelMockMethodMissingClassTest extends TestBase {

  private final AndroidApiLevel initialLibraryMockLevel = AndroidApiLevel.M;
  private final AndroidApiLevel finalLibraryMethodLevel = AndroidApiLevel.O_MR1;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    // TODO(b/197078995): Make this work on 12.
    assumeFalse(
        parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isEqualTo(Version.V12_0_0));
    boolean preMockApis =
        parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(initialLibraryMockLevel);
    boolean postMockApis =
        !preMockApis && parameters.getApiLevel().isGreaterThanOrEqualTo(finalLibraryMethodLevel);
    boolean betweenMockApis = !preMockApis && !postMockApis;
    Method addedOn23 = LibraryClass.class.getMethod("addedOn23");
    Method adeddOn27 = LibraryClass.class.getMethod("addedOn27");
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, initialLibraryMockLevel))
        .apply(
            setMockApiLevelForDefaultInstanceInitializer(
                LibraryClass.class, initialLibraryMockLevel))
        .apply(setMockApiLevelForMethod(addedOn23, initialLibraryMockLevel))
        .apply(setMockApiLevelForMethod(adeddOn27, finalLibraryMethodLevel))
        .compile()
        .applyIf(
            parameters.isDexRuntime()
                && parameters
                    .getRuntime()
                    .maxSupportedApiLevel()
                    .isGreaterThanOrEqualTo(initialLibraryMockLevel),
            b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(preMockApis, "Hello World")
        .assertSuccessWithOutputLinesIf(
            betweenMockApis,
            "LibraryClass::addedOn23",
            "LibraryClass::missingAndReferenced",
            "Hello World")
        .assertSuccessWithOutputLinesIf(
            postMockApis,
            "LibraryClass::addedOn23",
            "LibraryClass::missingAndReferenced",
            "LibraryCLass::addedOn27",
            "Hello World")
        .inspect(
            inspector -> {
              // TODO(b/204982782): Should be stubbed for api-level 1-23 with methods.
              assertThat(
                  inspector.clazz(ApiModelMockClassTest.LibraryClass.class), Matchers.isAbsent());
            });
  }

  // Only present from api level 23.
  public static class LibraryClass {

    public void addedOn23() {
      System.out.println("LibraryClass::addedOn23");
    }

    public void addedOn27() {
      System.out.println("LibraryCLass::addedOn27");
    }

    public void missingAndReferenced() {
      System.out.println("LibraryClass::missingAndReferenced");
    }

    public void missingNotReferenced() {
      System.out.println("LibraryClass::missingNotReferenced");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        LibraryClass libraryClass = new LibraryClass();
        libraryClass.addedOn23();
        libraryClass.missingAndReferenced();
        if (AndroidBuildVersion.VERSION >= 27) {
          libraryClass.addedOn27();
        }
      }
      System.out.println("Hello World");
    }
  }
}
