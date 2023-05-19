// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.synthesis.globals.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/***
 * This is a replication of b/268596049.
 */
@RunWith(Parameterized.class)
public class ApiModelD8GradleSetupTest extends TestBase {

  private static final AndroidApiLevel mockApiLevelOne = AndroidApiLevel.M;
  private static final AndroidApiLevel mockApiLevelTwo = AndroidApiLevel.O;
  private static final AndroidApiLevel mockApiLevelThree = AndroidApiLevel.R;

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addLibraryClasses(LibraryClassOne.class, LibraryClassTwo.class, LibraryClassThree.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .apply(setMockApiLevelForClass(LibraryClassOne.class, mockApiLevelOne))
        .apply(
            setMockApiLevelForMethod(
                LibraryClassOne.class.getDeclaredMethod("foo"), mockApiLevelOne))
        .apply(setMockApiLevelForClass(LibraryClassTwo.class, mockApiLevelTwo))
        .apply(
            setMockApiLevelForMethod(
                LibraryClassTwo.class.getDeclaredMethod("bar"), mockApiLevelTwo))
        .apply(setMockApiLevelForClass(LibraryClassThree.class, mockApiLevelThree))
        .apply(
            setMockApiLevelForMethod(
                LibraryClassThree.class.getDeclaredMethod("baz"), mockApiLevelThree))
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::enableOutliningOfMethods)
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck);
  }

  private boolean willHorizontallyMergeOutlines() {
    // After api level mockApiLevelTwo we only have a single outline and therefore will not merge.
    return parameters.getApiLevel().isLessThan(mockApiLevelTwo);
  }

  private boolean willStubLibraryClassThree() {
    return parameters.getApiLevel().isGreaterThan(AndroidApiLevel.L)
        && parameters.getApiLevel().isLessThan(mockApiLevelThree);
  }

  public AndroidApiLevel getApiLevelForRuntime() {
    return parameters.isCfRuntime()
        ? AndroidApiLevel.B
        : parameters.getRuntime().asDex().maxSupportedApiLevel();
  }

  public boolean addToBootClasspath(Class<?> clazz) {
    if (clazz == LibraryClassOne.class) {
      return getApiLevelForRuntime().isGreaterThanOrEqualTo(mockApiLevelOne);
    }
    if (clazz == LibraryClassTwo.class) {
      return getApiLevelForRuntime().isGreaterThanOrEqualTo(mockApiLevelTwo);
    }
    assert clazz == LibraryClassThree.class;
    return getApiLevelForRuntime().isGreaterThanOrEqualTo(mockApiLevelThree);
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class, ProgramClassOne.class, ProgramClassTwo.class)
        .addAndroidBuildVersion(AndroidApiLevel.B)
        .addLibraryClasses(LibraryClassOne.class, LibraryClassTwo.class, LibraryClassThree.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8DebugWithMerge() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testD8(
        CompilationMode.DEBUG,
        this::inspectNumberOfClassesFromOutput,
        HorizontallyMergedClassesInspector::assertNoClassesMerged);
  }

  @Test
  public void testD8ReleaseForApiLevelWithOutlining() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    assumeTrue(willHorizontallyMergeOutlines());
    testD8(
        CompilationMode.RELEASE,
        this::inspectNumberOfClassesFromOutput,
        HorizontallyMergedClassesInspector::assertNoClassesMerged);
  }

  @Test
  public void testD8ReleaseForApiLevelWithNoOutlining() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    assumeFalse(willHorizontallyMergeOutlines());
    testD8(
        CompilationMode.RELEASE,
        this::inspectNumberOfClassesFromOutput,
        HorizontallyMergedClassesInspector::assertNoClassesMerged);
  }

  private void testD8(
      CompilationMode mode,
      ThrowingConsumer<CodeInspector, Exception> inspect,
      ThrowableConsumer<HorizontallyMergedClassesInspector> horizontallyMergingConsumer)
      throws Exception {
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    D8TestCompileResult compileResultProgramClass =
        compileIntermediate(mode, globals, ProgramClassOne.class);
    D8TestCompileResult compileResultProgramClassTwo =
        compileIntermediate(mode, globals, ProgramClassTwo.class);
    D8TestCompileResult compileResultMain =
        compileIntermediate(
            mode, globals, Main.class, ProgramClassOne.class, ProgramClassTwo.class);

    if (willStubLibraryClassThree()) {
      assertTrue(globals.isSingleGlobal());
    } else {
      assertFalse(globals.hasGlobals());
    }

    List<Class<?>> bootClassPath = new ArrayList<>();
    if (addToBootClasspath(LibraryClassOne.class)) {
      bootClassPath.add(LibraryClassOne.class);
    }
    if (addToBootClasspath(LibraryClassTwo.class)) {
      bootClassPath.add(LibraryClassTwo.class);
    }
    if (addToBootClasspath(LibraryClassThree.class)) {
      bootClassPath.add(LibraryClassThree.class);
    }

    testForD8()
        .setMode(mode)
        .setUseDefaultRuntimeLibrary(false)
        .apply(b -> b.getBuilder().addGlobalSyntheticsResourceProviders(globals.getProviders()))
        .addProgramFiles(
            compileResultProgramClass.writeToZip(),
            compileResultProgramClassTwo.writeToZip(),
            compileResultMain.writeToZip())
        .setMinApi(parameters)
        .addAndroidBuildVersion(getApiLevelForRuntime())
        .addHorizontallyMergedClassesInspector(horizontallyMergingConsumer)
        .compile()
        .inspect(inspect)
        .addBootClasspathFiles(
            buildOnDexRuntime(parameters, bootClassPath.toArray(new Class<?>[0])))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  private D8TestCompileResult compileIntermediate(
      CompilationMode mode,
      GlobalSyntheticsTestingConsumer globals,
      Class<?> programClass,
      Class<?>... classpathClass)
      throws Exception {
    return testForD8(parameters.getBackend())
        .setMode(mode)
        .setIntermediate(true)
        .addProgramClasses(programClass)
        .addClasspathClasses(classpathClass)
        .apply(this::setupTestBuilder)
        .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals))
        .compile();
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    runResult.assertSuccessWithOutputLines(
        addToBootClasspath(LibraryClassOne.class) ? "LibraryClassOne::foo" : "Not calling foo()",
        addToBootClasspath(LibraryClassTwo.class) ? "LibraryClassTwo::bar" : "Not calling bar()",
        addToBootClasspath(LibraryClassThree.class)
            ? "LibraryClassThree::baz"
            : "Not calling baz()");
  }

  private void inspectNumberOfClassesFromOutput(CodeInspector inspector) {
    // We always have Main, ProgramClassOne, ProgramClassTwo and AndroidBuildVersion as program
    // classes. Depending on the api a number of synthetic classes.
    int numberOfClasses =
        4
            + (willStubLibraryClassThree()
                ? 2
                : (BooleanUtils.intValue(parameters.getApiLevel().isLessThan(mockApiLevelThree))))
            + BooleanUtils.intValue(parameters.getApiLevel().isLessThan(mockApiLevelTwo))
            + BooleanUtils.intValue(parameters.getApiLevel().isLessThan(mockApiLevelOne));
    assertEquals(numberOfClasses, inspector.allClasses().size());
    assertThat(inspector.clazz(Main.class), isPresent());
    assertThat(inspector.clazz(ProgramClassOne.class), isPresent());
  }

  // Will be present from api level 23
  public static class LibraryClassOne {

    public static void foo() {
      System.out.println("LibraryClassOne::foo");
    }
  }

  // Will be present from api level 26
  public static class LibraryClassTwo {

    public static void bar() {
      System.out.println("LibraryClassTwo::bar");
    }
  }

  // Will be present form api level 30
  public static class LibraryClassThree {

    public void baz() {
      System.out.println("LibraryClassThree::baz");
    }
  }

  public static class ProgramClassOne {

    public static void callOneAndTwo() {
      if (AndroidBuildVersion.VERSION >= 23) {
        LibraryClassOne.foo();
      } else {
        System.out.println("Not calling foo()");
      }
      if (AndroidBuildVersion.VERSION >= 26) {
        LibraryClassTwo.bar();
      } else {
        System.out.println("Not calling bar()");
      }
    }
  }

  public static class ProgramClassTwo extends LibraryClassThree {}

  public static class Main {

    public static void main(String[] args) {
      ProgramClassOne.callOneAndTwo();
      if (AndroidBuildVersion.VERSION >= 30) {
        new ProgramClassTwo().baz();
      } else {
        System.out.println("Not calling baz()");
      }
    }
  }
}
