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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineInstanceInitializerSuperTest extends TestBase {

  private static final AndroidApiLevel classApiLevel = AndroidApiLevel.M;

  private static final String[] EXPECTED = new String[] {"Hello ", "World!"};

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Main.class, ProgramExtendsLibraryClass.class)
        .setMinApi(parameters.getApiLevel())
        .addAndroidBuildVersion()
        .apply(setMockApiLevelForClass(LibraryClass.class, classApiLevel))
        .apply(
            setMockApiLevelForMethod(
                LibraryClass.class.getDeclaredConstructor(String.class), classApiLevel))
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
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
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
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(ProgramExtendsLibraryClass.class)
        .addDontObfuscate()
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void inspect(CodeInspector inspector, boolean isR8) throws Exception {
    // Because each of the outline context also have a super call, R8 will inline back the outline
    // because the super call has the same api level as the outlinee.
    verifyThat(inspector, parameters, LibraryClass.class.getMethod("print"))
        .isOutlinedFromUntil(
            ProgramExtendsLibraryClass.class.getMethod("print"),
            isR8 ? AndroidApiLevel.B : classApiLevel);
    verifyThat(inspector, parameters, LibraryClass.class.getDeclaredConstructor(String.class))
        .isOutlinedFromUntil(
            ProgramExtendsLibraryClass.class.getDeclaredConstructor(String.class),
            isR8 ? AndroidApiLevel.B : classApiLevel);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(classApiLevel)) {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    } else {
      runResult.assertSuccessWithOutputLines("Not calling API");
    }
  }

  // Only present from api level 23.
  public static class LibraryClass {

    private final String arg;

    public LibraryClass(String arg) {
      this.arg = arg;
    }

    public void print() {
      System.out.println(arg);
    }
  }

  public static class ProgramExtendsLibraryClass extends LibraryClass {

    private final LibraryClass otherArg;

    public ProgramExtendsLibraryClass(String arg) {
      super(arg); // <-- this cannot be outlined
      otherArg = new LibraryClass("World!"); // <-- this should be outlined.
    }

    @Override
    public void print() {
      super.print();
      otherArg.print();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 23) {
        new ProgramExtendsLibraryClass("Hello ").print();
      } else {
        System.out.println("Not calling API");
      }
    }
  }
}
