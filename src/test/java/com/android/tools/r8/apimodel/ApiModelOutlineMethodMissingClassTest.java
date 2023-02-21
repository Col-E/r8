// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Method;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineMethodMissingClassTest extends TestBase {

  private final AndroidApiLevel initialLibraryMockLevel = AndroidApiLevel.M;
  private final AndroidApiLevel finalLibraryMethodLevel = AndroidApiLevel.O_MR1;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private Method addedOn23() throws Exception {
    return LibraryClass.class.getMethod("addedOn23");
  }

  private Method addedOn27() throws Exception {
    return LibraryClass.class.getMethod("addedOn27");
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class, TestClass.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, initialLibraryMockLevel))
        .apply(setMockApiLevelForMethod(addedOn23(), initialLibraryMockLevel))
        .apply(setMockApiLevelForMethod(addedOn27(), finalLibraryMethodLevel))
        // TODO(b/213552119): Remove when enabled by default.
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses);
  }

  public boolean addToBootClasspath() {
    return parameters.isDexRuntime()
        && parameters
            .getRuntime()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(initialLibraryMockLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  @Test
  public void testD8Release() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) throws Exception {
    // No need to check further on CF.
    if (parameters.isCfRuntime()) {
      assertEquals(3, inspector.allClasses().size());
      return;
    }
    Method testMethod = TestClass.class.getDeclaredMethod("test");
    MethodSubject testMethodSubject = inspector.method(testMethod);
    assertThat(testMethodSubject, isPresent());
    Optional<FoundMethodSubject> synthesizedMissingNotReferenced =
        inspector.allClasses().stream()
            .flatMap(clazz -> clazz.allMethods().stream())
            .filter(
                methodSubject ->
                    methodSubject.isSynthetic()
                        && invokesMethodWithName("missingNotReferenced").matches(methodSubject))
            .findFirst();
    assertFalse(synthesizedMissingNotReferenced.isPresent());
    verifyThat(inspector, parameters, addedOn23())
        .isOutlinedFromUntil(testMethod, initialLibraryMockLevel);
    verifyThat(inspector, parameters, addedOn27())
        .isOutlinedFromUntil(testMethod, finalLibraryMethodLevel);
    verifyThat(inspector, parameters, LibraryClass.class.getDeclaredMethod("missingAndReferenced"))
        .isNotOutlinedFrom(testMethod);
    if (parameters.getApiLevel().isLessThan(initialLibraryMockLevel)) {
      assertEquals(5, inspector.allClasses().size());
    } else if (parameters.getApiLevel().isLessThan(finalLibraryMethodLevel)) {
      assertEquals(4, inspector.allClasses().size());
    } else {
      assertEquals(3, inspector.allClasses().size());
    }
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    boolean preMockApis =
        parameters.isCfRuntime() || parameters.getApiLevel().isLessThan(initialLibraryMockLevel);
    boolean postMockApis =
        !preMockApis && parameters.getApiLevel().isGreaterThanOrEqualTo(finalLibraryMethodLevel);
    if (preMockApis) {
      runResult.assertSuccessWithOutputLines("Hello World");
    } else if (postMockApis) {
      runResult.assertSuccessWithOutputLines(
          "LibraryClass::addedOn23",
          "LibraryClass::missingAndReferenced",
          "LibraryCLass::addedOn27",
          "Hello World");
    } else {
      runResult.assertSuccessWithOutputLines(
          "LibraryClass::addedOn23", "LibraryClass::missingAndReferenced", "Hello World");
    }
  }

  // Only present from api level 23.
  public static class LibraryClass {

    public void addedOn23() {
      System.out.println("LibraryClass::addedOn23");
    }

    public void addedOn27() {
      System.out.println("LibraryCLass::addedOn27");
    }

    public void missingAndReferenced() {
      System.out.println("LibraryClass::missingAndReferenced");
    }

    public void missingNotReferenced() {
      System.out.println("LibraryClass::missingNotReferenced");
    }
  }

  public static class TestClass {

    @NeverInline
    public static void test() {
      if (AndroidBuildVersion.VERSION >= 23) {
        LibraryClass libraryClass = new LibraryClass();
        libraryClass.addedOn23();
        libraryClass.missingAndReferenced();
        if (AndroidBuildVersion.VERSION >= 27) {
          libraryClass.addedOn27();
        }
      }
      System.out.println("Hello World");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      TestClass.test();
    }
  }
}
