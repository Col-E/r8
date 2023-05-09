// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.verifyThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.globals.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelMockExceptionTest extends TestBase {

  private final AndroidApiLevel mockSuperExceptionLevel = AndroidApiLevel.M;
  private final AndroidApiLevel mockSubExceptionLevel = AndroidApiLevel.P;

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private boolean isGreaterOrEqualToSuperMockLevel() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(mockSuperExceptionLevel);
  }

  private boolean isGreaterOrEqualToSubMockLevel() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(mockSubExceptionLevel);
  }

  private void setupTestCompileBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) {
    testBuilder
        .addProgramClasses(Main.class)
        .addLibraryClasses(LibrarySuperException.class, LibrarySubException.class, Thrower.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .apply(ApiModelingTestHelper::enableStubbingOfClassesAndDisableGlobalSyntheticCheck)
        .apply(setMockApiLevelForClass(LibrarySuperException.class, mockSuperExceptionLevel))
        .apply(setMockApiLevelForClass(LibrarySubException.class, mockSubExceptionLevel));
  }

  private void setupTestRuntimeBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) {
    testBuilder.setMinApi(parameters).addAndroidBuildVersion();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) {
    setupTestCompileBuilder(testBuilder);
    setupTestRuntimeBuilder(testBuilder);
  }

  private boolean addSuperToBootClasspath() {
    return parameters.isCfRuntime()
        || parameters
            .getRuntime()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(mockSuperExceptionLevel);
  }

  private boolean addSubToBootClasspath() {
    return parameters.isCfRuntime()
        || parameters
            .getRuntime()
            .maxSupportedApiLevel()
            .isGreaterThanOrEqualTo(mockSubExceptionLevel);
  }

  @Test
  public void testD8Debug() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .compile()
        .addBootClasspathClasses(Thrower.class)
        .applyIf(
            addSuperToBootClasspath(), b -> b.addBootClasspathClasses(LibrarySuperException.class))
        .applyIf(addSubToBootClasspath(), b -> b.addBootClasspathClasses(LibrarySubException.class))
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
        .addBootClasspathClasses(Thrower.class)
        .applyIf(
            addSuperToBootClasspath(), b -> b.addBootClasspathClasses(LibrarySuperException.class))
        .applyIf(addSubToBootClasspath(), b -> b.addBootClasspathClasses(LibrarySubException.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8MergeIndexed() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testD8Merge(OutputMode.DexIndexed);
  }

  @Test
  public void testD8MergeFilePerClass() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testD8Merge(OutputMode.DexFilePerClass);
  }

  @Test
  public void testD8MergeFilePerClassFile() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testD8Merge(OutputMode.DexFilePerClassFile);
  }

  public void testD8Merge(OutputMode outputMode) throws Exception {
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    Path incrementalOut =
        testForD8()
            .debug()
            .setOutputMode(outputMode)
            .setIntermediate(true)
            .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals))
            .apply(this::setupTestCompileBuilder)
            .compile()
            .writeToZip();

    if (isGreaterOrEqualToSubMockLevel()) {
      assertFalse(globals.hasGlobals());
    } else if (outputMode == OutputMode.DexIndexed) {
      assertTrue(globals.hasGlobals());
      assertTrue(globals.isSingleGlobal());
    } else {
      assertTrue(globals.hasGlobals());
      // The Main class reference the mock and should have globals.
      assertNotNull(globals.getProvider(Reference.classFromClass(Main.class)));
    }

    testForD8()
        .debug()
        .addProgramFiles(incrementalOut)
        .apply(b -> b.getBuilder().addGlobalSyntheticsResourceProviders(globals.getProviders()))
        .apply(this::setupTestRuntimeBuilder)
        .compile()
        .addBootClasspathClasses(Thrower.class)
        .applyIf(
            addSuperToBootClasspath(), b -> b.addBootClasspathClasses(LibrarySuperException.class))
        .applyIf(addSubToBootClasspath(), b -> b.addBootClasspathClasses(LibrarySubException.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .addKeepMainRule(Main.class)
        .compile()
        .addBootClasspathClasses(Thrower.class)
        .applyIf(
            addSuperToBootClasspath(), b -> b.addBootClasspathClasses(LibrarySuperException.class))
        .applyIf(addSubToBootClasspath(), b -> b.addBootClasspathClasses(LibrarySubException.class))
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    verifyThat(inspector, parameters, LibrarySuperException.class)
        .stubbedUntil(mockSuperExceptionLevel);
    verifyThat(inspector, parameters, LibrarySubException.class)
        .stubbedUntil(mockSubExceptionLevel);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (isGreaterOrEqualToSubMockLevel()) {
      runResult.assertSuccessWithOutputLines("Caught LibrarySubException");
    } else if (isGreaterOrEqualToSuperMockLevel()) {
      runResult.assertSuccessWithOutputLines("Caught LibrarySuperException");
    } else {
      runResult.assertSuccessWithOutputLines("Caught Exception");
    }
  }

  // Only present from api level M.
  public static class LibrarySuperException extends Exception {}

  // Only present from api level P.
  public static class LibrarySubException extends LibrarySuperException {}

  // Only present from api level P.
  public static class Thrower {

    public static void test(int apiVersion) throws Exception {
      if (apiVersion >= 28) {
        throw new LibrarySubException();
      } else if (apiVersion >= 23) {
        throw new LibrarySuperException();
      } else {
        throw new Exception();
      }
    }
  }

  public static class Main {

    public static void main(String[] args) {
      try {
        Thrower.test(AndroidBuildVersion.VERSION);
      } catch (LibrarySubException e) {
        System.out.println("Caught LibrarySubException");
      } catch (LibrarySuperException e) {
        System.out.println("Caught LibrarySuperException");
      } catch (Exception e) {
        System.out.println("Caught Exception");
      }
    }
  }
}
