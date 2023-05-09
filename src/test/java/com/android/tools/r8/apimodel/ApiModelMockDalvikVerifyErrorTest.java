// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/252558440. */
@RunWith(Parameterized.class)
public class ApiModelMockDalvikVerifyErrorTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final AndroidApiLevel mockLevel = AndroidApiLevel.M;

  private boolean isGreaterOrEqualToMockLevel() {
    return parameters.isDexRuntime() && parameters.getApiLevel().isGreaterThanOrEqualTo(mockLevel);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class)
        .addLibraryClasses(LibraryClass.class, LibrarySub.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(setMockApiLevelForClass(LibraryClass.class, mockLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, mockLevel))
        .apply(setMockApiLevelForMethod(LibraryClass.class.getDeclaredMethod("foo"), mockLevel));
  }

  private boolean addToBootClasspath() {
    return parameters.isCfRuntime()
        || parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(mockLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .inspect(this::inspect)
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
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .compile()
        .inspect(this::inspect)
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, LibrarySub.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (isGreaterOrEqualToMockLevel()) {
      runResult.assertFailureWithErrorThatThrows(ClassCastException.class);
    } else {
      runResult.assertSuccessWithOutputLines("Hello World!");
    }
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(LibraryClass.class), isAbsent());
  }

  public abstract static class LibraryClass {

    public abstract void foo();
  }

  public static class LibrarySub extends LibraryClass {

    @Override
    public void foo() {
      System.out.println("LibrarySub::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        LibraryClass librarySub = (LibraryClass) new Object();
        librarySub.foo();
      } else {
        System.out.println("Hello World!");
      }
    }
  }
}
