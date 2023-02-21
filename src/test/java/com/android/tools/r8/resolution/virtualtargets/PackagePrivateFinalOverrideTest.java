// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModel;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModelRunner;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModelRunnerWithCast;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackagePrivateFinalOverrideTest extends TestBase {

  private static final String[] EXPECTED =
      new String[] {"ViewModel.clear()", "MyViewModel.clear()", "ViewModel.clear()"};

  private static final String[] AMBIGUOUS_EXPECTED_OUTPUT =
      new String[] {"ViewModel.clear()", "MyViewModel.clear()", "MyViewModel.clear()"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PackagePrivateFinalOverrideTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClassesWithTestingAnnotations(
                    MyViewModel.class, ViewModel.class, Main.class, ViewModelRunner.class)
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(ViewModel.class, "clear", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        appView.definitionForProgramType(
            buildType(ViewModelRunner.class, appInfo.dexItemFactory()));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appInfo);
    assertTrue(lookupResult.isLookupResultSuccess());
    Set<String> targets = new HashSet<>();
    lookupResult.forEach(
        target -> targets.add(target.getDefinition().qualifiedName()), lambda -> fail());
    ImmutableSet<String> expected = ImmutableSet.of(ViewModel.class.getTypeName() + ".clear");
    assertEquals(expected, targets);
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    TestRunResult<?> runResult =
        testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
            .addProgramClasses(
                MyViewModel.class, ViewModel.class, Main.class, ViewModelRunner.class)
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertFailureWithErrorThatMatches(containsString("clear overrides final"));
    } else {
      runResult.assertSuccessWithOutputLines(EXPECTED);
    }
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(MyViewModel.class, Main.class, ViewModel.class, ViewModelRunner.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testResolutionWithInvalidInvoke() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClassesWithTestingAnnotations(
                    MyViewModel.class, ViewModel.class, Main.class, ViewModelRunner.class)
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(ViewModel.class, "clear", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        appView.definitionForProgramType(buildType(Main.class, appInfo.dexItemFactory()));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appInfo);
    assertTrue(lookupResult.isLookupResultFailure());
  }

  @Test
  public void testRuntimeWithInvalidInvoke()
      throws ExecutionException, CompilationFailedException, IOException {
    TestRunResult<?> runResult =
        testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
            .addProgramClasses(MyViewModel.class, ViewModel.class, ViewModelRunner.class)
            .addProgramClassFileData(getModifiedMainWithIllegalInvokeToViewModelClear())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertFailureWithErrorThatMatches(containsString("clear overrides final"));
    } else {
      runResult.assertFailureWithErrorThatThrows(IllegalAccessError.class);
    }
  }

  @Test
  public void testR8WithInvalidInvoke()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(MyViewModel.class, ViewModel.class, ViewModelRunner.class)
        .addProgramClassFileData(getModifiedMainWithIllegalInvokeToViewModelClear())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testResolutionWithAmbiguousInvoke() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClassesWithTestingAnnotations(
                    MyViewModel.class,
                    ViewModel.class,
                    Main.class,
                    ViewModelRunner.class,
                    ViewModelRunnerWithCast.class)
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(ViewModel.class, "clear", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        appView.definitionForProgramType(
            buildType(ViewModelRunnerWithCast.class, appInfo.dexItemFactory()));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appInfo);
    assertTrue(lookupResult.isLookupResultSuccess());
    Set<String> targets = new HashSet<>();
    lookupResult.forEach(
        target -> targets.add(target.getDefinition().qualifiedName()), lambda -> fail());
    ImmutableSet<String> expected = ImmutableSet.of(ViewModel.class.getTypeName() + ".clear");
    assertEquals(expected, targets);
  }

  @Test
  public void testRuntimeWithAmbiguousInvoke()
      throws ExecutionException, CompilationFailedException, IOException {
    TestRunResult<?> runResult =
        testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
            .addProgramClasses(MyViewModel.class, ViewModel.class, Main.class)
            .addProgramClassFileData(getModifiedViewModelRunnerWithDirectMyViewModelTarget())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertFailureWithErrorThatMatches(containsString("clear overrides final"));
    } else {
      runResult.assertSuccessWithOutputLines(AMBIGUOUS_EXPECTED_OUTPUT);
    }
  }

  @Test
  public void testR8WithAmbiguousInvoke()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClasses(MyViewModel.class, ViewModel.class, Main.class)
        .addProgramClassFileData(getModifiedViewModelRunnerWithDirectMyViewModelTarget())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(AMBIGUOUS_EXPECTED_OUTPUT);
  }

  private byte[] getModifiedMainWithIllegalInvokeToViewModelClear() throws IOException {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("clear")) {
                continuation.visitMethodInsn(
                    opcode,
                    DescriptorUtils.getBinaryNameFromJavaType(ViewModel.class.getTypeName()),
                    name,
                    descriptor,
                    isInterface);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  private byte[] getModifiedViewModelRunnerWithDirectMyViewModelTarget() throws IOException {
    return transformer(ViewModelRunnerWithCast.class)
        .setClassDescriptor(
            DescriptorUtils.javaTypeToDescriptor(ViewModelRunner.class.getTypeName()))
        .transformMethodInsnInMethod(
            "run",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("clearBridge")) {
                continuation.visitMethodInsn(opcode, owner, "clear", descriptor, isInterface);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public static class MyViewModel extends ViewModel {

    public void clear() {
      System.out.println("MyViewModel.clear()");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      MyViewModel myViewModel = new MyViewModel();
      myViewModel.clearBridge();
      myViewModel.clear();
      ViewModelRunner.run(myViewModel);
    }
  }
}
