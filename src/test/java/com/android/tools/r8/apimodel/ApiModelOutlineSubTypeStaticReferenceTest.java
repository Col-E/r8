// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineSubTypeStaticReferenceTest extends TestBase {

  private static final AndroidApiLevel libraryApiLevel = AndroidApiLevel.M;

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private boolean willInvokeLibraryMethods() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(libraryApiLevel);
  }

  @Test
  public void testD8BootClassPath() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    compileOnD8()
        .addBootClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkResultOnBootClassPath);
  }

  @Test
  public void testD8RuntimeClasspath() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    compileOnD8()
        .addRunClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkResultOnBootClassPath);
  }

  private D8TestCompileResult compileOnD8() throws Exception {
    return testForD8(parameters.getBackend())
        .addLibraryClasses(LibraryClass.class)
        .addProgramClasses(Main.class, Sub.class)
        .addAndroidBuildVersion()
        .setMinApi(parameters)
        .compile();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Main.class, Sub.class)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, libraryApiLevel))
        .apply(
            setMockApiLevelForMethod(LibraryClass.class.getDeclaredMethod("foo"), libraryApiLevel))
        // TODO(b/213552119): Remove when enabled by default.
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses);
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .applyIf(willInvokeLibraryMethods(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkResultOnBootClassPath);
  }

  @Test
  public void testD8Release() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .applyIf(willInvokeLibraryMethods(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkResultOnBootClassPath);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(Sub.class)
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .applyIf(willInvokeLibraryMethods(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkResultOnBootClassPath);
  }

  private void inspect(CodeInspector inspector, boolean isR8) throws Exception {
    Method otherMethod = Sub.class.getMethod("otherMethod");
    Method libraryMethod = LibraryClass.class.getMethod("foo");
    // TODO(b/254510678): R8 should not member-rebind to a potential non-existing method.
    verifyThat(
            inspector,
            parameters,
            isR8
                ? Reference.methodFromMethod(libraryMethod)
                : Reference.method(
                    Reference.classFromClass(Sub.class), "foo", Collections.emptyList(), null))
        .isOutlinedFromUntil(Sub.class.getDeclaredMethod("otherMethod"), libraryApiLevel);
  }

  private void checkResultOnBootClassPath(SingleTestRunResult<?> runResult) {
    runResult
        .assertSuccessWithOutputLinesIf(!willInvokeLibraryMethods(), "Not calling API")
        .assertSuccessWithOutputLinesIf(willInvokeLibraryMethods(), "Base::foo");
  }

  public static class LibraryClass {

    public void foo() {
      System.out.println("Base::foo");
    }
  }

  public static class Sub extends LibraryClass {

    public void otherMethod() {
      foo();
    }
  }

  public static class Main {
    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        new Sub().otherMethod();
      } else {
        System.out.println("Not calling API");
      }
    }
  }
}
