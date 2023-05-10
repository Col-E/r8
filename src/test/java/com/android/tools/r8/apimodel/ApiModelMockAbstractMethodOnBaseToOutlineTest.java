// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelMockAbstractMethodOnBaseToOutlineTest extends TestBase {

  private final AndroidApiLevel subMockLevel = AndroidApiLevel.M;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private boolean isGreaterOrEqualToMockLevel() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(subMockLevel);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class)
        .addLibraryClasses(
            LibraryClass.class, OtherLibraryClass.class, SubLibraryClassAtLaterApiLevel.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(setMockApiLevelForClass(LibraryClass.class, AndroidApiLevel.B))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass.class.getDeclaredMethod("foo"), AndroidApiLevel.B))
        .apply(
            setMockApiLevelForMethod(
                OtherLibraryClass.class.getDeclaredMethod("baz"), subMockLevel))
        .apply(setMockApiLevelForClass(SubLibraryClassAtLaterApiLevel.class, subMockLevel))
        .apply(
            setMockApiLevelForDefaultInstanceInitializer(
                SubLibraryClassAtLaterApiLevel.class, subMockLevel))
        .apply(
            setMockApiLevelForMethod(
                SubLibraryClassAtLaterApiLevel.class.getDeclaredMethod("foo"), subMockLevel));
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(this::inspect)
        .addBootClasspathClasses(LibraryClass.class)
        .applyIf(
            isGreaterOrEqualToMockLevel(),
            b ->
                b.addBootClasspathClasses(
                    OtherLibraryClass.class, SubLibraryClassAtLaterApiLevel.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(runResult -> checkOutput(runResult, false));
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(this::inspect)
        .addBootClasspathClasses(LibraryClass.class)
        .applyIf(
            isGreaterOrEqualToMockLevel(),
            b ->
                b.addBootClasspathClasses(
                    OtherLibraryClass.class, SubLibraryClassAtLaterApiLevel.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(runResult -> checkOutput(runResult, true));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect)
        .addBootClasspathClasses(LibraryClass.class)
        .applyIf(
            isGreaterOrEqualToMockLevel(),
            b ->
                b.addBootClasspathClasses(
                    OtherLibraryClass.class, SubLibraryClassAtLaterApiLevel.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(runResult -> checkOutput(runResult, true));
  }

  private void checkOutput(SingleTestRunResult<?> runResult, boolean isRelease) {
    if (isGreaterOrEqualToMockLevel()) {
      runResult.assertSuccessWithOutputLines(
          "OtherLibraryClass::foo", "SubLibraryClassAtLaterApiLevel::foo");
    } else {
      runResult.assertSuccessWithOutputLines("NoClassDefFoundError");
    }
    runResult.applyIf(
        !isGreaterOrEqualToMockLevel()
            && parameters.isDexRuntime()
            && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
        result -> result.assertStderrMatches(not(containsString("This dex file is invalid"))));
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(SubLibraryClassAtLaterApiLevel.class), isAbsent());
  }

  public abstract static class LibraryClass {

    public abstract void foo();
  }

  public static class SubLibraryClassAtLaterApiLevel extends LibraryClass {

    @Override
    public void foo() {
      System.out.println("SubLibraryClassAtLaterApiLevel::foo");
    }
  }

  public static class OtherLibraryClass {

    public static void baz() {
      System.out.println("OtherLibraryClass::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      try {
        OtherLibraryClass.baz();
        if (AndroidBuildVersion.VERSION >= 23) {
          callSubFoo(new SubLibraryClassAtLaterApiLevel());
        } else if (System.currentTimeMillis() == 0) {
          callSubFoo(new Object());
        }
      } catch (NoClassDefFoundError e) {
        System.out.println("NoClassDefFoundError");
      }
    }

    @NeverInline
    private static void callSubFoo(Object o) {
      ((SubLibraryClassAtLaterApiLevel) o).foo();
    }
  }
}
