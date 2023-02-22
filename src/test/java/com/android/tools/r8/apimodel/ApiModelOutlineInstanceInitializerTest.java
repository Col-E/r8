// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
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
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineInstanceInitializerTest extends TestBase {

  private static final AndroidApiLevel classApiLevel = AndroidApiLevel.M;

  private static final String[] EXPECTED =
      new String[] {"LibraryClass::<clinit>", "Argument::<clinit>", "Hello World!"};

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addLibraryClasses(LibraryClass.class, Argument.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(Argument.class, classApiLevel))
        .apply(
            setMockApiLevelForMethod(
                Argument.class.getDeclaredConstructor(String.class), classApiLevel))
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass.class.getDeclaredConstructor(Argument.class), classApiLevel))
        .apply(setMockApiLevelForMethod(LibraryClass.class.getMethod("print"), classApiLevel))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::disableStubbingOfClasses);
  }

  public boolean addToBootClasspath() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(classApiLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, Argument.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Release() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, Argument.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .applyIf(
            addToBootClasspath(),
            b -> b.addBootClasspathClasses(LibraryClass.class, Argument.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void inspect(CodeInspector inspector, boolean isR8) throws Exception {
    Method mainMethod = Main.class.getMethod("main", String[].class);
    verifyThat(inspector, parameters, Argument.class.getDeclaredConstructor(String.class))
        .isOutlinedFromBetween(mainMethod, AndroidApiLevel.L, classApiLevel);
    verifyThat(inspector, parameters, LibraryClass.class.getDeclaredConstructor(Argument.class))
        .isOutlinedFromBetween(mainMethod, AndroidApiLevel.L, classApiLevel);
    // For R8 we inline into the method with an instance initializer when we do not outline it.
    verifyThat(inspector, parameters, LibraryClass.class.getMethod("print"))
        .isOutlinedFromBetween(
            mainMethod, isR8 ? AndroidApiLevel.L : AndroidApiLevel.B, classApiLevel);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(classApiLevel)) {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    } else {
      runResult.assertSuccessWithOutputLines("Not calling API");
    }
  }

  public static class Argument {

    private final String string;

    static {
      System.out.println("Argument::<clinit>");
    }

    public Argument(String string) {
      this.string = string;
    }

    @Override
    public String toString() {
      return string;
    }
  }

  // Only present from api level 23.
  public static class LibraryClass {

    private final Argument argument;

    static {
      System.out.println("LibraryClass::<clinit>");
    }

    public LibraryClass(Argument argument) {
      this.argument = argument;
    }

    public void print() {
      System.out.println(argument.toString());
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        LibraryClass libraryClass = new LibraryClass(new Argument("Hello World!"));
        libraryClass.print();
      } else {
        System.out.println("Not calling API");
      }
    }
  }
}
