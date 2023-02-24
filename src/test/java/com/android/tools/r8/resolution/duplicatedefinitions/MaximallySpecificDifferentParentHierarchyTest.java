// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.duplicatedefinitions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8FullTestBuilder;
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
 * I: I_L { f }
 * J: J_L extends I { }, J_P { f }
 * class Main implements I, J
 * </pre>
 */
public class MaximallySpecificDifferentParentHierarchyTest extends TestBase {

  private static final String EXPECTED = "I::foo";

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
            ToolHelper.getClassFileForTestClass(I.class),
            ToolHelper.getClassFileForTestClass(J.class))
        .build();
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AndroidApp.Builder builder = AndroidApp.builder();
    builder
        .addProgramFiles(ToolHelper.getClassFileForTestClass(Main.class))
        .addClassProgramData(getJProgram());
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
    Set<String> failedTypes = new HashSet<>();
    methodResolutionResult.forEachMethodResolutionResult(
        result -> {
          if (result.isSingleResolution()) {
            SingleResolutionResult<?> resolution = result.asSingleResolution();
            methodResults.add(
                (resolution.getResolvedHolder().isProgramClass() ? "Program: " : "Library: ")
                    + resolution.getResolvedMethod().getReference().toString());
          } else {
            assertTrue(result.isFailedResolution());
            result
                .asFailedResolution()
                .forEachFailureDependency(
                    type -> failedTypes.add(type.toDescriptorString()), m -> fail());
          }
        });
    assertEquals(
        ImmutableSet.of(
            "Library: void " + typeName(I.class) + ".foo()",
            "Program: void " + typeName(J.class) + ".foo()"),
        methodResults);
    assertEquals(ImmutableSet.of(descriptor(J.class)), failedTypes);
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addRunClasspathFiles(libraryClasses)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getJProgram())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .apply(this::setupTestBuilder)
        .compile()
        .addBootClasspathFiles(buildOnDexRuntime(parameters, libraryClasses))
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/230289235): Extend to support multiple definition results.
        .assertFailureWithErrorThatThrowsIf(
            !parameters.canUseDefaultAndStaticInterfaceMethods(),
            IncompatibleClassChangeError.class)
        .assertSuccessWithOutputLinesIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(), EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    // TODO(b/230289235): Extend to support multiple definition results.
    R8FullTestBuilder testBuilder = testForR8(parameters.getBackend()).addKeepMainRule(Main.class);
    testBuilder
        .apply(this::setupTestBuilder)
        .compile()
        .addBootClasspathFiles(buildOnDexRuntime(parameters, libraryClasses))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  private void setupTestBuilder(TestCompilerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    testBuilder
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getJProgram())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryFiles(libraryClasses)
        .setMinApi(parameters)
        .addOptionsModification(options -> options.loadAllClassDefinitions = true);
  }

  private byte[] getJProgram() throws Exception {
    return transformer(JProgram.class).setClassDescriptor(descriptor(J.class)).transform();
  }

  public interface I {
    default void foo() {
      System.out.println("I::foo");
    }
  }

  public interface JProgram {
    default void foo() {
      System.out.println("J_Program::foo");
    }
  }

  public interface J extends I {}

  public static class Main implements I, J {

    public static void main(String[] args) {
      new Main().foo();
    }
  }
}
