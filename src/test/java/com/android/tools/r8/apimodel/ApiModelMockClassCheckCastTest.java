// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
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
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelMockClassCheckCastTest extends TestBase {

  private final AndroidApiLevel mockLevel = AndroidApiLevel.M;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private boolean isGreaterOrEqualToMockLevel() {
    return parameters.isDexRuntime() && parameters.getApiLevel().isGreaterThanOrEqualTo(mockLevel);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) {
    testBuilder
        .addProgramClasses(Main.class, TestClass.class)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(setMockApiLevelForClass(LibraryClass.class, mockLevel));
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class, TestClass.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(isGreaterOrEqualToMockLevel(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(parameters.getBackend())
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .applyIf(isGreaterOrEqualToMockLevel(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .applyIf(isGreaterOrEqualToMockLevel(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (isGreaterOrEqualToMockLevel()) {
      runResult.assertSuccessWithOutputLines("false", "checkcast caused ClassCastException");
    } else {
      runResult.assertSuccessWithOutputLines(
          "instanceof caused NoClassDefFoundError", "checkcast caused NoClassDefFoundError");
    }
    runResult.applyIf(
        !isGreaterOrEqualToMockLevel()
            && parameters.isDexRuntime()
            && parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
        result -> result.assertStderrMatches(not(containsString("This dex file is invalid"))));
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(LibraryClass.class), isAbsent());
  }

  // Only present from api level 23.
  public static class LibraryClass {}

  public static class TestClass {

    @NeverInline
    public static void testInstanceOf(Object o) {
      try {
        System.out.println(o instanceof LibraryClass);
      } catch (NoClassDefFoundError ex) {
        System.out.println("instanceof caused NoClassDefFoundError");
      }
    }

    @NeverInline
    public static void testCheckCast(Object o) {
      try {
        System.out.println(((LibraryClass) o).getClass().getName());
      } catch (NoClassDefFoundError e) {
        System.out.println("checkcast caused NoClassDefFoundError");
      } catch (ClassCastException e) {
        System.out.println("checkcast caused ClassCastException");
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (System.currentTimeMillis() > 0) {
        Object o = new Object();
        TestClass.testInstanceOf(o);
        TestClass.testCheckCast(o);
      } else {
        LibraryClass libraryClass = new LibraryClass();
        TestClass.testInstanceOf(libraryClass);
        TestClass.testCheckCast(libraryClass);
      }
    }
  }
}
