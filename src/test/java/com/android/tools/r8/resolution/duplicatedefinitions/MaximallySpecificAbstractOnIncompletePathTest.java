// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.duplicatedefinitions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Reproduction of b/231928368. This is testing resolving Main.f for:
 *
 * <pre>
 * I: I_L { abstract f }, I_P { }
 * class Main implements I
 * </pre>
 */
@RunWith(Parameterized.class)
public class MaximallySpecificAbstractOnIncompletePathTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private Path libraryClasses;

  @Before
  public void setup() throws Exception {
    libraryClasses = temp.newFile("lib.jar").toPath();
    ZipBuilder.builder(libraryClasses)
        .addFilesRelative(
            ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(I.class))
        .build();
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(
            AndroidApp.builder()
                .addClassProgramData(getMainWithoutFoo(), getIOnProgram())
                .addLibraryFiles(parameters.getDefaultRuntimeLibrary(), libraryClasses)
                .build(),
            null,
            options -> options.loadAllClassDefinitions = true);
    AppInfoWithClassHierarchy appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(Main.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult methodResolutionResult =
        appInfo.unsafeResolveMethodDueToDexFormat(method);
    assertTrue(methodResolutionResult.isSingleResolution());
    SingleResolutionResult<?> resolution = methodResolutionResult.asSingleResolution();
    assertTrue(resolution.getResolvedHolder().isLibraryClass());
    assertEquals(
        "void " + typeName(I.class) + ".foo()", resolution.getResolvedMethod().toSourceString());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addRunClasspathFiles(libraryClasses)
        .addProgramClassFileData(getMainWithoutFoo(), getIOnProgram())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(AbstractMethodError.class);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    Version dexRuntime = parameters.getDexRuntimeVersion();
    testForD8(parameters.getBackend())
        .apply(this::setupTestBuilder)
        .compile()
        .addBootClasspathFiles(buildOnDexRuntime(parameters, libraryClasses))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrowsIf(dexRuntime.isDalvik(), VerifyError.class)
        .assertFailureWithErrorThatThrowsIf(!dexRuntime.isDalvik(), AbstractMethodError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(Main.class)
        .apply(this::setupTestBuilder)
        .compile()
        .addBootClasspathFiles(buildOnDexRuntime(parameters, libraryClasses))
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/230289235): Extend to support multiple definition results.
        .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClassFileData(getMainWithoutFoo(), getIOnProgram())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryFiles(libraryClasses)
        .setMinApi(parameters)
        .addOptionsModification(options -> options.loadAllClassDefinitions = true);
  }

  private byte[] getMainWithoutFoo() throws Exception {
    return transformer(Main.class).removeMethods(MethodPredicate.onName("foo")).transform();
  }

  private byte[] getIOnProgram() throws Exception {
    return transformer(I.class).removeMethods(MethodPredicate.all()).transform();
  }

  public interface I {
    void foo();
  }

  public static class Main implements I {

    public static void main(String[] args) {
      new Main().foo();
    }

    @Override
    public void foo() {
      System.out.println("Should have been removed");
    }
  }
}
