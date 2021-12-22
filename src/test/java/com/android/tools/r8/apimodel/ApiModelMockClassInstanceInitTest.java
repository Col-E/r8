// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelMockClassInstanceInitTest extends TestBase {

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
        .addProgramClasses(Main.class, TestClass.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addAndroidBuildVersion()
        .apply(ApiModelingTestHelper::enableStubbingOfClasses)
        .apply(setMockApiLevelForClass(LibraryClass.class, mockLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, mockLevel))
        .enableInliningAnnotations()
        .compile()
        .applyIf(isMockApiLevel, b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(isMockApiLevel, "LibraryClass::foo")
        .assertSuccessWithOutputLinesIf(!isMockApiLevel, "NoClassDefFoundError")
        .inspect(
            inspector ->
                verifyThat(inspector, parameters, LibraryClass.class).stubbedUntil(mockLevel))
        .applyIf(
            !isMockApiLevel
                && parameters.isDexRuntime()
                && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
            result -> result.assertStderrMatches(not(containsString("This dex file is invalid"))));
  }

  // Only present from api level 23.
  public static class LibraryClass {

    public void foo() {
      System.out.println("LibraryClass::foo");
    }
  }

  public static class TestClass {

    @NeverInline
    public static void test() {
      try {
        new LibraryClass().foo();
      } catch (ExceptionInInitializerError | NoClassDefFoundError er) {
        System.out.println("NoClassDefFoundError");
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      TestClass.test();
    }
  }
}
