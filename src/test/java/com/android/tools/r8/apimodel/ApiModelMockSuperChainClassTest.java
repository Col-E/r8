// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
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

@RunWith(Parameterized.class)
public class ApiModelMockSuperChainClassTest extends TestBase {

  private final AndroidApiLevel mockApiLevel = AndroidApiLevel.N;
  private final AndroidApiLevel lowerMockApiLevel = AndroidApiLevel.M;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private boolean isGreaterOrEqualToMockLevel() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(mockApiLevel);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) {
    testBuilder
        .addProgramClasses(Main.class, ProgramClass.class)
        .addLibraryClasses(LibraryClass.class, OtherLibraryClass.class, LibraryInterface.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(setMockApiLevelForClass(LibraryClass.class, lowerMockApiLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, lowerMockApiLevel))
        .apply(setMockApiLevelForClass(OtherLibraryClass.class, mockApiLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(OtherLibraryClass.class, mockApiLevel))
        .apply(setMockApiLevelForClass(LibraryInterface.class, lowerMockApiLevel));
  }

  private boolean addLibraryClassesToBootClasspath() {
    return parameters.isDexRuntime()
        && parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(lowerMockApiLevel);
  }

  private boolean addOtherLibraryClassesToBootClasspath() {
    return parameters.isDexRuntime()
        && parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(mockApiLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(
            addLibraryClassesToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, LibraryInterface.class))
        .applyIf(
            addOtherLibraryClassesToBootClasspath(),
            b -> b.addBootClasspathClasses(OtherLibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  @Test
  public void testD8Release() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(
            addLibraryClassesToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, LibraryInterface.class))
        .applyIf(
            addOtherLibraryClassesToBootClasspath(),
            b -> b.addBootClasspathClasses(OtherLibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .addKeepClassRules(ProgramClass.class)
        .compile()
        .applyIf(
            addLibraryClassesToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, LibraryInterface.class))
        .applyIf(
            addOtherLibraryClassesToBootClasspath(),
            b -> b.addBootClasspathClasses(OtherLibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    verifyThat(inspector, parameters, LibraryClass.class)
        .stubbedBetween(AndroidApiLevel.L_MR1, lowerMockApiLevel);
    verifyThat(inspector, parameters, LibraryInterface.class)
        .stubbedBetween(AndroidApiLevel.L_MR1, lowerMockApiLevel);
    verifyThat(inspector, parameters, OtherLibraryClass.class)
        .stubbedBetween(AndroidApiLevel.L_MR1, mockApiLevel);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (isGreaterOrEqualToMockLevel()) {
      runResult.assertSuccessWithOutputLines("ProgramClass::foo");
    } else {
      runResult.assertSuccessWithOutputLines("Hello World");
    }
  }

  // Only present from api level 23.
  public static class LibraryClass {}

  // Only present from api level 23
  public interface LibraryInterface {}

  // Only present from api level 24
  public static class OtherLibraryClass extends LibraryClass {}

  public static class ProgramClass extends OtherLibraryClass implements LibraryInterface {

    public void foo() {
      System.out.println("ProgramClass::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 24) {
        new ProgramClass().foo();
      } else {
        System.out.println("Hello World");
      }
    }
  }
}
