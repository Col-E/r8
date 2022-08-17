// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForField;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineInstanceFieldTest extends TestBase {

  private static final AndroidApiLevel classApiLevel = AndroidApiLevel.M;
  private static final AndroidApiLevel fieldApiLevel = AndroidApiLevel.P;

  private static final String EXPECTED = "LibraryClass::field";

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Main.class)
        .setMinApi(parameters.getApiLevel())
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, classApiLevel))
        .apply(setMockApiLevelForField(LibraryClass.class.getField("field"), fieldApiLevel))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses);
  }

  public boolean addToBootClasspath() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(classApiLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        // TODO(b/242078642):  We should outline field references to newer api levels.
        .inspect(ApiModelingTestHelper::assertNoSynthesizedClasses)
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Release() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        // TODO(b/242078642):  We should outline field references to newer api levels.
        .inspect(ApiModelingTestHelper::assertNoSynthesizedClasses)
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .compile()
        // TODO(b/242078642):  We should outline field references to newer api levels.
        .inspect(ApiModelingTestHelper::assertNoSynthesizedClasses)
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(classApiLevel)) {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    } else {
      runResult.assertSuccessWithOutputLines("Not calling API");
    }
  }

  // Only present from api level 23.
  public static class LibraryClass {

    public String field = "LibraryClass::field";
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        LibraryClass libraryClass = new LibraryClass();
        System.out.println(libraryClass.field);
      } else {
        System.out.println("Not calling API");
      }
    }
  }
}
