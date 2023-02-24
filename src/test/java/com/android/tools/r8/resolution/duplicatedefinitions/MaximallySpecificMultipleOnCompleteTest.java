// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.duplicatedefinitions;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
/**
 * This is testing resolving Main.f for:
 *
 * <pre>
 * I: I_L { f }
 * J: J_L extends I { f }, J_P extends I { f }
 * K: K_P extends J { f }
 * L: L_L { f }
 * M: M_L extends L { }, M_P extends L { }
 * class Main implements I,J,K,M
 * </pre>
 */
public class MaximallySpecificMultipleOnCompleteTest extends TestBase {

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
            ToolHelper.getClassPathForTests(),
            ToolHelper.getClassFileForTestClass(J.class),
            ToolHelper.getClassFileForTestClass(I.class),
            ToolHelper.getClassFileForTestClass(L.class),
            ToolHelper.getClassFileForTestClass(M.class))
        .build();
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AndroidApp.Builder builder = AndroidApp.builder();
    builder
        .addProgramFiles(
            ToolHelper.getClassFileForTestClass(K.class),
            ToolHelper.getClassFileForTestClass(M.class))
        .addClassProgramData(ImmutableList.of(getJOnProgram(), getMainWithAllImplements()));
    builder.addLibraryFiles(parameters.getDefaultRuntimeLibrary(), libraryClasses);
    AppView<AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(
            builder.build(), null, options -> options.loadAllClassDefinitions = true);
    AppInfoWithClassHierarchy appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(Main.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult methodResolutionResult =
        appInfo.unsafeResolveMethodDueToDexFormat(method);
    assertTrue(methodResolutionResult.isIncompatibleClassChangeErrorResult());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addRunClasspathFiles(libraryClasses)
        .addProgramClasses(K.class, M.class)
        .addProgramClassFileData(getJOnProgram(), getMainWithAllImplements())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    runTest(testForD8(parameters.getBackend()));
  }

  @Test
  public void testR8() throws Exception {
    runTest(testForR8(parameters.getBackend()).addKeepMainRule(Main.class));
  }

  private void runTest(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(K.class, M.class)
        .addProgramClassFileData(getJOnProgram(), getMainWithAllImplements())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryFiles(libraryClasses)
        .setMinApi(parameters)
        .addOptionsModification(options -> options.loadAllClassDefinitions = true)
        .compile()
        .addBootClasspathFiles(buildOnDexRuntime(parameters, libraryClasses))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  private byte[] getJOnProgram() throws Exception {
    return transformer(JProgram.class).setClassDescriptor(descriptor(J.class)).transform();
  }

  private byte[] getMainWithAllImplements() throws Exception {
    return transformer(Main.class).setImplements(I.class, J.class, K.class, M.class).transform();
  }

  public interface I {
    default void foo() {
      System.out.println("I::foo");
    }
  }

  /* Present on both library and program */
  public interface JProgram extends I {
    @Override
    default void foo() {
      System.out.println("J_Program::foo");
      ;
    }
  }

  public interface J extends I {
    @Override
    default void foo() {
      System.out.println("J_Library::foo");
      ;
    }
  }

  public interface K extends J {

    @Override
    default void foo() {
      System.out.println("J::foo");
    }
  }

  public interface L {

    default void foo() {
      System.out.println("L::foo");
    }
  }

  public interface M extends L {}

  public static class Main implements I, J, K /*, M */ {

    public static void main(String[] args) {
      new Main().foo();
    }
  }
}
