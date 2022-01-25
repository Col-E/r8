// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineMethodUnknownTest extends TestBase {

  private static final AndroidApiLevel classApiLevel = AndroidApiLevel.M;

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
    boolean willInvokeLibraryMethods =
        parameters.isDexRuntime() && parameters.getApiLevel().isGreaterThanOrEqualTo(classApiLevel);
    testForR8(parameters.getBackend())
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses)
        .compile()
        .inspect(
            inspector -> {
              // Assert that we did not outline any methods.
              assertEquals(
                  0,
                  inspector.allClasses().stream()
                      .filter(FoundClassSubject::isCompilerSynthesized)
                      .count());
            })
        .applyIf(willInvokeLibraryMethods, b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(!willInvokeLibraryMethods, "Not calling API")
        .assertSuccessWithOutputLinesIf(willInvokeLibraryMethods, "LibraryClass::unknownApiLevel");
  }

  // Only present from api level 23.
  public static class LibraryClass {

    public static void unknownApiLevel() {
      System.out.println("LibraryClass::unknownApiLevel");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        LibraryClass.unknownApiLevel();
      } else {
        System.out.println("Not calling API");
      }
    }
  }
}
