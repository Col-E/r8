// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModel;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModelRunner;
import com.android.tools.r8.resolution.virtualtargets.package_a.ViewModelRunnerWithCast;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PackagePrivateFinalOverrideTest extends TestBase {

  private static final String[] EXPECTED =
      new String[] {"ViewModel.clear()", "MyViewModel.clear()", "ViewModel.clear()"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PackagePrivateFinalOverrideTest(TestParameters parameters) {
    this.parameters = parameters;
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
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(
                MyViewModel.class, Main.class, ViewModel.class, ViewModelRunner.class)
            .addKeepMainRule(Main.class)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertFailureWithErrorThatMatches(containsString("overrides final"));
    } else {
      runResult.assertFailureWithErrorThatMatches(containsString("java.lang.NullPointerException"));
    }
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
      runResult.assertFailureWithErrorThatMatches(containsString("java.lang.IllegalAccessError"));
    }
  }

  @Test
  public void testR8WithInvalidInvoke()
      throws ExecutionException, CompilationFailedException, IOException {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(MyViewModel.class, ViewModel.class, ViewModelRunner.class)
            .addProgramClassFileData(getModifiedMainWithIllegalInvokeToViewModelClear())
            .addKeepMainRule(Main.class)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertFailureWithErrorThatMatches(containsString("overrides final"));
    } else {
      // TODO(b/149363086): Ensure the error is similar to runtime for package override.
      runResult.assertFailure();
    }
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
      runResult.assertSuccessWithOutputLines(
          "ViewModel.clear()", "MyViewModel.clear()", "MyViewModel.clear()");
    }
  }

  @Test
  public void testR8WithAmbiguousInvoke()
      throws ExecutionException, CompilationFailedException, IOException {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(MyViewModel.class, ViewModel.class, Main.class)
            .addProgramClassFileData(getModifiedViewModelRunnerWithDirectMyViewModelTarget())
            .addKeepMainRule(Main.class)
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertFailureWithErrorThatMatches(containsString("overrides final"));
    } else {
      runResult.assertFailureWithErrorThatMatches(containsString("java.lang.NullPointerException"));
    }
  }

  private byte[] getModifiedMainWithIllegalInvokeToViewModelClear() throws IOException {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("clear")) {
                continuation.apply(
                    opcode,
                    DescriptorUtils.getBinaryNameFromJavaType(ViewModel.class.getTypeName()),
                    name,
                    descriptor,
                    isInterface);
              } else {
                continuation.apply(opcode, owner, name, descriptor, isInterface);
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
                continuation.apply(opcode, owner, "clear", descriptor, isInterface);
              } else {
                continuation.apply(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  @NeverClassInline
  public static class MyViewModel extends ViewModel {

    @NeverInline
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
