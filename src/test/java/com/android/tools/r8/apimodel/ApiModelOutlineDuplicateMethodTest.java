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
import static org.junit.Assert.assertTrue;
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
public class ApiModelOutlineDuplicateMethodTest extends TestBase {

  private final AndroidApiLevel classApiLevel = AndroidApiLevel.K;
  private final AndroidApiLevel methodApiLevel = AndroidApiLevel.M;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private Method addedOn23() throws Exception {
    return LibraryClass.class.getMethod("addedOn23");
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class, TestClass.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(setMockApiLevelForMethod(addedOn23(), methodApiLevel))
        // TODO(b/213552119): Remove when enabled by default.
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses);
  }

  public boolean addToBootClasspath() {
    return parameters.isDexRuntime()
        && parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(classApiLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
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
    testForD8()
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
    int classCount =
        parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(methodApiLevel) ? 4 : 3;
    assertEquals(classCount, inspector.allClasses().size());
    Method testMethod = TestClass.class.getDeclaredMethod("test");
    verifyThat(inspector, parameters, addedOn23()).isOutlinedFromUntil(testMethod, methodApiLevel);
    if (parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(methodApiLevel)) {
      // Verify that we invoke the synthesized outline, addedOn23, twice.
      Optional<FoundMethodSubject> synthesizedAddedOn23 =
          inspector.allClasses().stream()
              .flatMap(clazz -> clazz.allMethods().stream())
              .filter(
                  methodSubject ->
                      methodSubject.isSynthetic()
                          && invokesMethodWithName("addedOn23").matches(methodSubject))
              .findFirst();
      assertTrue(synthesizedAddedOn23.isPresent());
      MethodSubject testMethodSubject = inspector.method(testMethod);
      assertThat(testMethodSubject, isPresent());
      assertEquals(
          2,
          testMethodSubject
              .streamInstructions()
              .filter(
                  instructionSubject -> {
                    if (!instructionSubject.isInvoke()) {
                      return false;
                    }
                    return instructionSubject
                        .getMethod()
                        .asMethodReference()
                        .equals(synthesizedAddedOn23.get().asMethodReference());
                  })
              .count());
    }
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(methodApiLevel)) {
      runResult.assertSuccessWithOutputLines(
          "LibraryClass::addedOn23", "LibraryClass::addedOn23", "Hello World");
    } else {
      runResult.assertSuccessWithOutputLines("Hello World");
    }
  }

  // Only present from api level 19.
  public static class LibraryClass {

    public static void addedOn23() {
      System.out.println("LibraryClass::addedOn23");
    }
  }

  public static class TestClass {

    @NeverInline
    public static void test() {
      if (AndroidBuildVersion.VERSION >= 23) {
        LibraryClass.addedOn23();
        LibraryClass.addedOn23();
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
