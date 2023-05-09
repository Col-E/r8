// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoReturnTypeStrengthening;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoOutlineForFullyMockedTest extends TestBase {

  private final AndroidApiLevel libraryApiLevel = AndroidApiLevel.M;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    Method methodOn23 = LibraryClass.class.getDeclaredMethod("methodOn23");
    testBuilder
        .addProgramClasses(Main.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, libraryApiLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, libraryApiLevel))
        .apply(setMockApiLevelForMethod(methodOn23, libraryApiLevel))
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck);
  }

  private boolean addToBootClasspath() {
    return parameters.isDexRuntime()
        && parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(libraryApiLevel);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    String result;
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(libraryApiLevel)) {
      result = StringUtils.lines("LibraryClass::methodOn23", "Hello World");
    } else {
      result = StringUtils.lines("Hello World");
    }
    testForD8()
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(result)
        .inspect(inspector -> inspect(inspector, true, true));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoReturnTypeStrengtheningAnnotations()
        .compile()
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(inspector -> inspect(inspector, true, true));
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(libraryApiLevel)) {
      runResult.assertSuccessWithOutputLines("LibraryClass::methodOn23", "Hello World");
    } else {
      runResult.assertSuccessWithOutputLines("Hello World");
    }
  }

  private void inspect(CodeInspector inspector, boolean canOutline, boolean canStub)
      throws Exception {
    Method methodOn23 = LibraryClass.class.getDeclaredMethod("methodOn23");
    Method mainMethod = Main.class.getDeclaredMethod("main", String[].class);
    assertThat(inspector.method(mainMethod), isPresent());
    if (canOutline) {
      verifyThat(inspector, parameters, methodOn23)
          .isOutlinedFromUntil(mainMethod, libraryApiLevel);
    } else {
      verifyThat(inspector, parameters, methodOn23).isNotOutlinedFrom(mainMethod);
    }
    assertThat(inspector.clazz(LibraryClass.class), isAbsent());
  }

  // Only present from api level 23.
  public static class LibraryClass {

    public void methodOn23() {
      System.out.println("LibraryClass::methodOn23");
    }
  }

  public static class Main {

    @NeverInline
    // TODO(b/214329925): Type strengthening should consult API database.
    @NoReturnTypeStrengthening
    public static Object create() {
      return AndroidBuildVersion.VERSION >= 23 ? new LibraryClass() : null;
    }

    public static void main(String[] args) {
      Object libraryClass = create();
      if (libraryClass != null) {
        ((LibraryClass) libraryClass).methodOn23();
      }
      System.out.println("Hello World");
    }
  }
}
