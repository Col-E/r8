// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.Matchers;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelMockInheritedClassTest extends TestBase {

  private final AndroidApiLevel mockLevel = AndroidApiLevel.M;

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
    boolean isMockApiLevel =
        parameters.isDexRuntime() && parameters.getApiLevel().isGreaterThanOrEqualTo(mockLevel);
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, ProgramClass.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addKeepClassRules(ProgramClass.class)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, mockLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, mockLevel))
        .compile()
        .applyIf(
            parameters.isDexRuntime()
                && parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(mockLevel),
            b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(isMockApiLevel, "ProgramClass::foo")
        .assertSuccessWithOutputLinesIf(!isMockApiLevel, "Hello World")
        .inspect(
            inspector ->
                // TODO(b/204982782): These should be stubbed for api-level 1-23.
                assertThat(inspector.clazz(LibraryClass.class), Matchers.isAbsent()));
  }

  // Only present from api level 23.
  public static class LibraryClass {}

  public static class ProgramClass extends LibraryClass {

    public void foo() {
      System.out.println("ProgramClass::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        new ProgramClass().foo();
      } else {
        System.out.println("Hello World");
      }
    }
  }
}
