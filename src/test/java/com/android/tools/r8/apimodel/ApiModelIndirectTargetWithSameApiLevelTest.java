// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.apimodel.ApiModelingTestHelper.ApiModelingMethodVerificationHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// The difference between this test and the ApiModelIndirectTargetWithDifferentApiLevelTest is
// what we should rebind to. If there is a method definition in the class hierarchy and it has
// the same api-level as one in an interface we should still pick the class.
@RunWith(Parameterized.class)
public class ApiModelIndirectTargetWithSameApiLevelTest extends TestBase {

  private final AndroidApiLevel mockApiLevel = AndroidApiLevel.M;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private boolean isGreaterOrEqualToMockLevel() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(mockApiLevel);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class, ProgramJoiner.class)
        .addLibraryClasses(LibraryClass.class, LibraryInterface.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion(parameters.getApiLevel())
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, mockApiLevel))
        .apply(setMockApiLevelForClass(LibraryClass.class, mockApiLevel))
        .apply(setMockApiLevelForMethod(LibraryClass.class.getDeclaredMethod("foo"), mockApiLevel))
        .apply(setMockApiLevelForClass(LibraryInterface.class, mockApiLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryInterface.class.getDeclaredMethod("foo"), mockApiLevel));
  }

  private void setupRunEnvironment(TestCompileResult<?, ?> compileResult) {
    compileResult.applyIf(
        isGreaterOrEqualToMockLevel(),
        b -> b.addRunClasspathClasses(LibraryClass.class, LibraryInterface.class));
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class, ProgramJoiner.class)
        .addAndroidBuildVersion(parameters.getApiLevel())
        .addLibraryClasses(LibraryClass.class, LibraryInterface.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, false));
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .apply(this::setupRunEnvironment)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, false))
        .inspect(inspector -> inspect(inspector, false));
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .apply(this::setupRunEnvironment)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, false))
        .inspect(inspector -> inspect(inspector, false));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(ProgramJoiner.class)
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .apply(this::setupRunEnvironment)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, true));
  }

  private void checkOutput(SingleTestRunResult<?> runResult, boolean isR8) {
    if (isGreaterOrEqualToMockLevel()) {
      runResult.assertSuccessWithOutputLines("LibraryClass::foo");
    } else if (isR8 && parameters.isCfRuntime()) {
      // TODO(b/254510678): R8 should not rebind to the library method.
      runResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
    } else {
      runResult.assertSuccessWithOutputLines("Hello World");
    }
    runResult.applyIf(
        !isGreaterOrEqualToMockLevel()
            && parameters.isDexRuntime()
            && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
        result -> result.assertStderrMatches(not(containsString("This dex file is invalid"))));
  }

  private void inspect(CodeInspector inspector, boolean isR8) throws Exception {
    ApiModelingMethodVerificationHelper verifyHelper =
        verifyThat(
            inspector,
            parameters,
            Reference.method(
                // TODO(b/254510678): Due to member rebinding, we rebind ProgramJoiner.foo() to
                //  LibraryClass.foo().
                Reference.classFromClass(isR8 ? LibraryClass.class : ProgramJoiner.class),
                "foo",
                Collections.emptyList(),
                null));
    if (isR8 && parameters.isCfRuntime()) {
      verifyHelper.isOutlinedFromUntil(
          Main.class.getDeclaredMethod("main", String[].class), mockApiLevel);
    } else {
      verifyHelper.isOutlinedFromUntilAlsoForCf(
          Main.class.getDeclaredMethod("main", String[].class), mockApiLevel);
    }
  }

  // Only present from api level 23.
  public static class LibraryClass {

    // Only present from api level 27;
    public void foo() {
      System.out.println("LibraryClass::foo");
    }
  }

  // Present from 23
  public interface LibraryInterface {

    // Present from 23
    void foo();
  }

  public static class ProgramJoiner extends LibraryClass implements LibraryInterface {}

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        new ProgramJoiner().foo();
      } else {
        System.out.println("Hello World");
      }
    }
  }
}
