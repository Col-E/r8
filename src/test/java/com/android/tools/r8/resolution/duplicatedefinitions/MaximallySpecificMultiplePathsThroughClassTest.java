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
 * I: I_L { f }, I_P { f }
 * A: A_P implements I { }
 * J: J_P { f }
 * class Main extends A implements J
 * </pre>
 */
public class MaximallySpecificMultiplePathsThroughClassTest extends TestBase {

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
    AndroidApp.Builder builder = AndroidApp.builder();
    builder
        .addProgramFiles(
            ToolHelper.getClassFileForTestClass(Main.class),
            ToolHelper.getClassFileForTestClass(J.class))
        .addClassProgramData(getAWithImplementsI())
        .addClassProgramData(getIProgram());
    builder.addLibraryFiles(parameters.getDefaultRuntimeLibrary(), libraryClasses);
    AppView<AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(
            builder.build(), null, options -> options.loadAllClassDefinitions = true);
    AppInfoWithClassHierarchy appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(Main.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult methodResolutionResult =
        appInfo.unsafeResolveMethodDueToDexFormat(method);
    assertTrue(methodResolutionResult.isMultiMethodResolutionResult());
    Set<String> methodResults = new HashSet<>();
    methodResolutionResult.forEachMethodResolutionResult(
        result -> {
          assertTrue(result.isSingleResolution());
          SingleResolutionResult<?> resolution = result.asSingleResolution();
          methodResults.add(
              (resolution.getResolvedHolder().isProgramClass() ? "Program: " : "Library: ")
                  + resolution.getResolvedMethod().getReference().toString());
        });
    assertEquals(
        ImmutableSet.of(
            "Library: void " + typeName(I.class) + ".foo()",
            "Program: void " + typeName(I.class) + ".foo()",
            "Program: void " + typeName(J.class) + ".foo()"),
        methodResults);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addRunClasspathFiles(libraryClasses)
        .addProgramClasses(J.class, Main.class)
        .addProgramClassFileData(getAWithImplementsI(), getIProgram())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    runTest(testForD8(parameters.getBackend()), IncompatibleClassChangeError.class);
  }

  @Test
  public void testR8() throws Exception {
    // TODO(b/230289235): Extend to support multiple definition results.
    runTest(
        testForR8(parameters.getBackend()).addKeepMainRule(Main.class),
        IncompatibleClassChangeError.class);
  }

  private void runTest(
      TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder, Class<? extends Throwable> errorClass)
      throws Exception {
    testBuilder
        .addProgramClasses(J.class, Main.class)
        .addProgramClassFileData(getAWithImplementsI(), getIProgram())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryFiles(libraryClasses)
        .setMinApi(parameters)
        .addOptionsModification(options -> options.loadAllClassDefinitions = true)
        .compile()
        .addBootClasspathFiles(buildOnDexRuntime(parameters, libraryClasses))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(errorClass);
  }

  private byte[] getAWithImplementsI() throws Exception {
    return transformer(A.class).setImplements(I.class).transform();
  }

  private byte[] getIProgram() throws Exception {
    return transformer(IProgram.class).setClassDescriptor(descriptor(I.class)).transform();
  }

  public interface I {
    default void foo() {
      System.out.println("I::foo");
    }
  }

  public interface IProgram {
    default void foo() {
      System.out.println("I_Program::foo");
    }
  }

  public interface J {
    default void foo() {
      System.out.println("J::foo");
    }
  }

  public static class A /* implements I */ {}

  public static class Main extends A implements J {

    public static void main(String[] args) {
      new Main().foo();
    }
  }
}
