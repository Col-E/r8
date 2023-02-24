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
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
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
 * class Main implements I,J,K
 * </pre>
 */
public class MaximallySpecificSingleDominatingSubTest extends TestBase {

  private static final String EXPECTED = "K::foo";

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
            ToolHelper.getClassFileForTestClass(I.class))
        .build();
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AndroidApp.Builder builder = AndroidApp.builder();
    builder
        .addProgramFiles(
            ToolHelper.getClassFileForTestClass(K.class),
            ToolHelper.getClassFileForTestClass(Main.class))
        .addClassProgramData(ImmutableList.of(getJOnProgram()));
    builder.addLibraryFiles(parameters.getDefaultRuntimeLibrary(), libraryClasses);
    AppView<AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(
            builder.build(), null, options -> options.loadAllClassDefinitions = true);
    AppInfoWithClassHierarchy appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(Main.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult methodResolutionResult =
        appInfo.unsafeResolveMethodDueToDexFormat(method);
    assertTrue(methodResolutionResult.isSingleResolution());
    Set<String> methodResults = new HashSet<>();
    methodResolutionResult.forEachMethodResolutionResult(
        result -> {
          assertTrue(result.isSingleResolution());
          SingleResolutionResult<?> resolution = result.asSingleResolution();
          methodResults.add(
              (resolution.getResolvedHolder().isProgramClass() ? "Program: " : "Library: ")
                  + resolution.getResolvedMethod().getReference().toString());
        });
    assertEquals(ImmutableSet.of("Program: void " + typeName(K.class) + ".foo()"), methodResults);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addRunClasspathFiles(libraryClasses)
        .addProgramClasses(K.class, Main.class)
        .addProgramClassFileData(getJOnProgram())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
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
        .addProgramClasses(K.class, Main.class)
        .addProgramClassFileData(getJOnProgram())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryFiles(libraryClasses)
        .setMinApi(parameters)
        .addOptionsModification(options -> options.loadAllClassDefinitions = true)
        .compile()
        .addBootClasspathFiles(buildOnDexRuntime(parameters, libraryClasses))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private byte[] getJOnProgram() throws Exception {
    return transformer(JProgram.class).setClassDescriptor(descriptor(J.class)).transform();
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
      System.out.println("K::foo");
    }
  }

  public static class Main implements I, J, K {

    public static void main(String[] args) {
      new Main().foo();
    }
  }
}
