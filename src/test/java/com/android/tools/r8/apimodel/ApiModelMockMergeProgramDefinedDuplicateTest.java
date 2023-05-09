// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForDefaultInstanceInitializer;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.globals.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelMockMergeProgramDefinedDuplicateTest extends TestBase {

  private final AndroidApiLevel mockLevel = AndroidApiLevel.M;

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private boolean isGreaterOrEqualToMockLevel() {
    return parameters.isDexRuntime() && parameters.getApiLevel().isGreaterThanOrEqualTo(mockLevel);
  }

  private void setupTestCompileBuilder(
      TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder, Class<?> programClass)
      throws NoSuchMethodException {
    testBuilder
        .addProgramClasses(programClass)
        .addLibraryClasses(LibraryClass.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(ApiModelingTestHelper::disableOutlining)
        .apply(setMockApiLevelForClass(LibraryClass.class, mockLevel))
        .apply(setMockApiLevelForDefaultInstanceInitializer(LibraryClass.class, mockLevel))
        .apply(setMockApiLevelForMethod(LibraryClass.class.getDeclaredMethod("foo"), mockLevel))
        .apply(setMockApiLevelForMethod(LibraryClass.class.getDeclaredMethod("bar"), mockLevel));
  }

  private boolean addToBootClasspath() {
    return parameters.isDexRuntime()
        && parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(mockLevel);
  }

  @Test
  public void testD8DebugDexFilePerClassFile() throws Exception {
    testD8Merge(CompilationMode.DEBUG);
  }

  @Test
  public void testD8ReleaseDexFilePerClassFile() throws Exception {
    testD8Merge(CompilationMode.RELEASE);
  }

  private Path runD8ForClass(
      Class<?> clazz, GlobalSyntheticsTestingConsumer global, CompilationMode mode)
      throws Exception {
    return testForD8()
        .setMode(mode)
        .setOutputMode(OutputMode.DexFilePerClassFile)
        .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(global))
        .apply(builder -> setupTestCompileBuilder(builder, clazz))
        .compile()
        .writeToZip();
  }

  public void testD8Merge(CompilationMode mode) throws Exception {
    List<Path> paths = new ArrayList<>();
    GlobalSyntheticsTestingConsumer mainGlobals = new GlobalSyntheticsTestingConsumer();
    GlobalSyntheticsTestingConsumer testCallingFooGlobals = new GlobalSyntheticsTestingConsumer();
    GlobalSyntheticsTestingConsumer testCallingBarGlobals = new GlobalSyntheticsTestingConsumer();
    paths.add(runD8ForClass(Main.class, mainGlobals, mode));
    paths.add(runD8ForClass(TestCallingFoo.class, testCallingFooGlobals, mode));
    paths.add(runD8ForClass(TestCallingBar.class, testCallingBarGlobals, mode));
    assertFalse(mainGlobals.hasGlobals());
    assertFalse(testCallingFooGlobals.hasGlobals());
    assertFalse(testCallingBarGlobals.hasGlobals());
    testForD8()
        .setMode(mode)
        .addProgramFiles(paths)
        // Add the actual LibraryClass definition to the code, thus the globals will need to merge
        // the synthetic stubs but also disregard them due to a program definition being present.
        .addProgramClasses(LibraryClass.class)
        .setMinApi(parameters)
        .apply(
            b ->
                b.getBuilder()
                    .addGlobalSyntheticsResourceProviders(testCallingFooGlobals.getProviders())
                    .addGlobalSyntheticsResourceProviders(testCallingBarGlobals.getProviders()))
        .compile()
        .inspect(this::inspect)
        .applyIf(addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject libraryClassSubject = inspector.clazz(LibraryClass.class);
    assertThat(libraryClassSubject, isPresent());
    assertThat(libraryClassSubject.uniqueMethodWithOriginalName("foo"), isPresent());
    assertThat(libraryClassSubject.uniqueMethodWithOriginalName("bar"), isPresent());
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    runResult.assertSuccessWithOutputLines("LibraryClass::foo", "LibraryClass::bar");
  }

  // Only present form api level 23, but also included in program.
  public static class LibraryClass {

    public void foo() {
      System.out.println("LibraryClass::foo");
    }

    public void bar() {
      System.out.println("LibraryClass::bar");
    }
  }

  public static class TestCallingFoo {

    public static void callFoo() {
      new LibraryClass().foo();
    }
  }

  public static class TestCallingBar {

    public static void callBar() {
      new LibraryClass().bar();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      TestCallingFoo.callFoo();
      TestCallingBar.callBar();
    }
  }
}
