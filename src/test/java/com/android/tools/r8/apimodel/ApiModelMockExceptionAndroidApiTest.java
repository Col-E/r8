// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.globals.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelMockExceptionAndroidApiTest extends TestBase {

  private final AndroidApiLevel apiLevelForIllFormedLocaleException = AndroidApiLevel.P;
  private final String illFormedLocaleException = "android.icu.util.IllformedLocaleException";

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  private boolean isGreaterOrEqualToExceptionLevel() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelForIllFormedLocaleException);
  }

  private void setupTestCompileBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder)
      throws Exception {
    testBuilder
        .addProgramClassFileData(
            transformer(Main.class)
                .transformTryCatchBlock(
                    "main",
                    (start, end, handler, type, visitor) -> {
                      if (type.equals(binaryName(LibraryExceptionPlaceHolder.class))) {
                        visitor.visitTryCatchBlock(
                            start,
                            end,
                            handler,
                            DescriptorUtils.getBinaryNameFromJavaType(illFormedLocaleException));
                      } else {
                        visitor.visitTryCatchBlock(start, end, handler, type);
                      }
                    })
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(LibraryExceptionPlaceHolder.class),
                    DescriptorUtils.javaTypeToDescriptor(illFormedLocaleException))
                .transform())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST))
        .setMinApi(parameters)
        .apply(ApiModelingTestHelper::enableStubbingOfClasses);
  }

  private void setupTestRuntimeBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) {
    testBuilder.setMinApi(parameters).addAndroidBuildVersion();
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    setupTestCompileBuilder(testBuilder);
    setupTestRuntimeBuilder(testBuilder);
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8()
        .setMode(CompilationMode.DEBUG)
        .apply(this::setupTestBuilder)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8()
        .setMode(CompilationMode.RELEASE)
        .apply(this::setupTestBuilder)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testD8MergeIndexed() throws Exception {
    testD8Merge(OutputMode.DexIndexed);
  }

  @Test
  public void testD8MergeFilePerClass() throws Exception {
    testD8Merge(OutputMode.DexFilePerClass);
  }

  @Test
  public void testD8MergeFilePerClassFile() throws Exception {
    testD8Merge(OutputMode.DexFilePerClassFile);
  }

  private void testD8Merge(OutputMode outputMode) throws Exception {
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

    if (isGreaterOrEqualToExceptionLevel()) {
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
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkOutput);
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .apply(this::setupTestBuilder)
            .addKeepMainRule(Main.class)
            .enableInliningAnnotations()
            .compile();
    compile.run(parameters.getRuntime(), Main.class).apply(this::checkOutput);
  }

  private void checkOutput(SingleTestRunResult<?> runResult) {
    if (isGreaterOrEqualToExceptionLevel()) {
      runResult.assertSuccessWithOutputLines("Caught LibraryException");
    } else {
      runResult.assertSuccessWithOutputLines("Caught Exception");
    }
  }

  // Only present from api level M.
  public static class LibraryExceptionPlaceHolder /* android.icu.util.IllformedLocaleException */
      extends Exception {}

  public static class Main {

    @NeverInline
    public static void test(int apiVersion) throws Exception {
      if (apiVersion >= 28) {
        throw new /* android.icu.util.IllformedLocaleException() */ LibraryExceptionPlaceHolder();
      } else {
        throw new Exception();
      }
    }

    public static void main(String[] args) {
      try {
        test(AndroidBuildVersion.VERSION);
      } catch (LibraryExceptionPlaceHolder /* android.icu.util.IllformedLocaleException */ e) {
        System.out.println("Caught LibraryException");
      } catch (Exception e) {
        System.out.println("Caught Exception");
      }
    }
  }
}
