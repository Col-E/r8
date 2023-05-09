// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.syntheticApiOutlineClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelAndroidxApiImplTest extends TestBase {

  private static final String newTestApi26Descriptor = "Landroidx/TestApi26ImplDescriptor;";

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
        .addProgramClassFileData(
            transformer(TestApi26Impl.class).setClassDescriptor(newTestApi26Descriptor).transform(),
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(TestApi26Impl.class), newTestApi26Descriptor)
                .transform())
        .addLibraryClasses(LibraryClass23.class, LibraryClass26.class, LibraryClass30.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion(getMaxSupportedApiLevel())
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(setMockApiLevelForClass(LibraryClass23.class, AndroidApiLevel.M))
        .apply(setMockApiLevelForClass(LibraryClass26.class, AndroidApiLevel.O))
        .apply(setMockApiLevelForClass(LibraryClass30.class, AndroidApiLevel.R))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass23.class.getDeclaredMethod("foo"), AndroidApiLevel.M))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass26.class.getDeclaredMethod("bar"), AndroidApiLevel.O))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass30.class.getDeclaredMethod("baz"), AndroidApiLevel.R));
  }

  private void setupRuntime(TestCompileResult<?, ?> compileResult) throws Exception {
    if (!parameters.isDexRuntime()) {
      return;
    }
    AndroidApiLevel maxSupportedApiLevel = parameters.asDexRuntime().maxSupportedApiLevel();
    if (maxSupportedApiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.R)) {
      compileResult.addBootClasspathFiles(
          buildOnDexRuntime(
              parameters, LibraryClass23.class, LibraryClass26.class, LibraryClass30.class));
    } else if (maxSupportedApiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.O)) {
      compileResult.addBootClasspathFiles(
          buildOnDexRuntime(parameters, LibraryClass23.class, LibraryClass26.class));
    } else if (maxSupportedApiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.M)) {
      compileResult.addBootClasspathFiles(buildOnDexRuntime(parameters, LibraryClass23.class));
    }
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Release() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .apply(this::setupRuntime)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void inspect(CodeInspector inspector, boolean isR8) {
    assertThat(inspector.clazz(Main.class), isPresent());
    ClassReference classReference = Reference.classFromDescriptor(newTestApi26Descriptor);
    if (parameters.isCfRuntime()) {
      assertThat(inspector.clazz(classReference), isPresent());
    } else {
      assertThat(
          inspector.clazz(classReference),
          notIf(
              isPresent(),
              isR8 && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O)));
      assertThat(
          inspector.clazz(syntheticApiOutlineClass(classReference, 0)),
          notIf(isPresent(), parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.R)));
      assertThat(inspector.clazz(syntheticApiOutlineClass(classReference, 1)), not(isPresent()));
    }
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (getMaxSupportedApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.R)) {
      runResult.assertSuccessWithOutputLines(
          "LibraryClass23::foo", "LibraryClass26::bar", "LibraryClass30::baz");
    } else if (getMaxSupportedApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O)) {
      runResult.assertSuccessWithOutputLines("LibraryClass23::foo", "LibraryClass26::bar");
    } else if (getMaxSupportedApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.M)) {
      runResult.assertSuccessWithOutputLines("LibraryClass23::foo");
    } else {
      runResult.assertSuccessWithOutputLines("Api version not high enough");
    }
  }

  public static class LibraryClass23 {

    public static void foo() {
      System.out.println("LibraryClass23::foo");
    }
  }

  public static class LibraryClass26 {

    public static void bar() {
      System.out.println("LibraryClass26::bar");
    }
  }

  public static class LibraryClass30 {

    public static void baz() {
      System.out.println("LibraryClass30::baz");
    }
  }

  public static class /* androidx. */ TestApi26Impl {

    public static void has23Reference() {
      LibraryClass23.foo();
    }

    public static void has26Reference() {
      LibraryClass26.bar();
    }

    public static void has30Reference() {
      LibraryClass30.baz();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        TestApi26Impl.has23Reference();
        if (AndroidBuildVersion.VERSION >= 26) {
          TestApi26Impl.has26Reference();
          if (AndroidBuildVersion.VERSION >= 30) {
            TestApi26Impl.has30Reference();
          }
        }
      } else {
        System.out.println("Api version not high enough");
      }
    }
  }
}
