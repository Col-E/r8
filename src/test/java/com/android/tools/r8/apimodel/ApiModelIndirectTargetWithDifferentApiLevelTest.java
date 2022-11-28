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
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
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

@RunWith(Parameterized.class)
public class ApiModelIndirectTargetWithDifferentApiLevelTest extends TestBase {

  private final AndroidApiLevel ifaceApiLevel = AndroidApiLevel.M;
  private final AndroidApiLevel classApiLevel = AndroidApiLevel.M;
  private final AndroidApiLevel classMethodApiLevel = AndroidApiLevel.O_MR1;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private boolean isGreaterOrEqualToIfaceMockLevel() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(ifaceApiLevel);
  }

  private boolean isGreaterOrEqualToClassMethodMockLevel() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(classMethodApiLevel);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class, ProgramJoiner.class)
        .addLibraryClasses(LibraryClass.class, LibraryInterface.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters.getApiLevel())
        .addAndroidBuildVersion(parameters.getApiLevel())
        .apply(ApiModelingTestHelper::enableStubbingOfClasses)
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, classApiLevel))
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass.class.getDeclaredMethod("foo"), classMethodApiLevel))
        .apply(setMockApiLevelForClass(LibraryInterface.class, ifaceApiLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryInterface.class.getDeclaredMethod("foo"), ifaceApiLevel));
  }

  private void setupRunEnvironment(TestCompileResult<?, ?> compileResult) {
    compileResult
        .applyIf(
            isGreaterOrEqualToClassMethodMockLevel(),
            b -> b.addRunClasspathClasses(LibraryClass.class, LibraryInterface.class))
        .applyIf(
            !isGreaterOrEqualToClassMethodMockLevel() && isGreaterOrEqualToIfaceMockLevel(),
            b ->
                b.addRunClasspathClasses(LibraryInterface.class)
                    .addRunClasspathClassFileData(
                        transformer(LibraryClass.class).removeMethodsWithName("foo").transform()));
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime() && parameters.getApiLevel().isEqualTo(AndroidApiLevel.B));
    testForJvm()
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
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, false));
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .apply(this::setupRunEnvironment)
        .inspect(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(ProgramJoiner.class)
        .compile()
        .inspect(this::inspect)
        .apply(this::setupRunEnvironment)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkOutput(result, true));
  }

  private void checkOutput(SingleTestRunResult<?> runResult, boolean isR8) {
    if (isGreaterOrEqualToClassMethodMockLevel()) {
      runResult.assertSuccessWithOutputLines("LibraryClass::foo");
    } else if (isGreaterOrEqualToIfaceMockLevel()) {
      if (isR8) {
        runResult.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
      } else {
        runResult.assertFailureWithErrorThatThrows(AbstractMethodError.class);
      }
    } else if (isR8 && parameters.isCfRuntime()) {
      // TODO(b/254510678): R8 should not rebind to the library method.
      runResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
    } else {
      runResult.assertSuccessWithOutputLines("Hello World");
    }
    runResult.applyIf(
        !isGreaterOrEqualToIfaceMockLevel()
            && parameters.isDexRuntime()
            && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
        result -> result.assertStderrMatches(not(containsString("This dex file is invalid"))));
  }

  private void inspect(CodeInspector inspector) throws Exception {
    // TODO(b/254510678): We should outline the call to ProgramJoiner.foo.
    verifyThat(
            inspector,
            parameters,
            Reference.method(
                Reference.classFromClass(ProgramJoiner.class),
                "foo",
                Collections.emptyList(),
                null))
        .isNotOutlinedFrom(Main.class.getDeclaredMethod("main", String[].class));
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
