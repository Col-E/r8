// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/269097876. */
@RunWith(Parameterized.class)
public class ApiModelFieldAssignNewInstanceTest extends TestBase {

  private final AndroidApiLevel mockApiLevel = AndroidApiLevel.R;

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private AndroidApiLevel getMaxSupportedApiLevel() {
    return parameters.isCfRuntime()
        ? AndroidApiLevel.B
        : parameters.asDexRuntime().maxSupportedApiLevel();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Helper.class, Main.class)
        .addLibraryClasses(LibraryClass.class, SubLibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion(getMaxSupportedApiLevel())
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(setMockApiLevelForClass(LibraryClass.class, AndroidApiLevel.B))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass.class.getDeclaredMethod("foo"), AndroidApiLevel.B))
        .apply(setMockApiLevelForClass(SubLibraryClass.class, mockApiLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(SubLibraryClass.class, mockApiLevel))
        .apply(
            setMockApiLevelForMethod(SubLibraryClass.class.getDeclaredMethod("foo"), mockApiLevel));
  }

  private void setupRuntime(TestCompileResult<?, ?> compileResult) throws Exception {
    if (getMaxSupportedApiLevel().isGreaterThanOrEqualTo(mockApiLevel)) {
      compileResult.addBootClasspathFiles(
          buildOnDexRuntime(parameters, LibraryClass.class, SubLibraryClass.class));
    } else {
      compileResult.addBootClasspathFiles(buildOnDexRuntime(parameters, LibraryClass.class));
    }
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm(parameters)
        .addProgramClasses(Helper.class, Main.class)
        .addLibraryClasses(LibraryClass.class)
        .addAndroidBuildVersion(AndroidApiLevel.B)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, false));
  }

  @Test
  public void testD8WithoutModeling() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClasses(Helper.class, Main.class)
        .addLibraryClasses(LibraryClass.class, SubLibraryClass.class)
        .addAndroidBuildVersion(getMaxSupportedApiLevel())
        .addOptionsModification(options -> options.apiModelingOptions().disableApiModeling())
        .setMinApi(parameters)
        .compile()
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, false));
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(this::inspect)
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, true));
  }

  @Test
  public void testD8Release() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(this::inspect)
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, true));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .enableNoAccessModificationAnnotationsForMembers()
        .compile()
        .inspect(this::inspect)
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, true));
  }

  private void inspect(CodeInspector inspector) throws Exception {
    assertThat(inspector.clazz(Main.class), isPresent());
    assertThat(inspector.clazz(Helper.class), isPresent());
    Method getLibraryClass = Helper.class.getMethod("setLibraryClass");
    verifyThat(inspector, parameters, SubLibraryClass.class.getConstructor())
        .isOutlinedFromBetween(getLibraryClass, AndroidApiLevel.L, mockApiLevel);
  }

  private void checkOutput(SingleTestRunResult<?> runResult, boolean apiModelingEnabled) {
    if (parameters.isCfRuntime()) {
      runResult.assertFailureWithErrorThatThrows(ClassNotFoundException.class);
    } else if (getMaxSupportedApiLevel().isGreaterThanOrEqualTo(mockApiLevel)) {
      runResult.assertSuccessWithOutputLines("SubLibraryClass::foo");
    } else {
      runResult.assertSuccessWithOutputLines("LibraryClass::foo");
    }
  }

  // Present from 1
  public static class LibraryClass {

    public void foo() {
      System.out.println("LibraryClass::foo");
    }
  }

  // Present from 30
  public static class SubLibraryClass extends LibraryClass {

    @Override
    public void foo() {
      System.out.println("SubLibraryClass::foo");
    }
  }

  public static class Helper {

    @NoAccessModification private LibraryClass libraryClass;

    public void setLibraryClass() {
      if (AndroidBuildVersion.VERSION >= 30) {
        libraryClass = new SubLibraryClass();
      } else {
        libraryClass = new LibraryClass();
      }
    }

    public LibraryClass getLibraryClass() {
      return libraryClass;
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Helper helper = new Helper();
      helper.setLibraryClass();
      helper.getLibraryClass().foo();
    }
  }
}
