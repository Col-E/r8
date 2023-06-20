// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PreserveIllegalAccessErrorInitialMethodResolutionHolderTest extends TestBase {

  private static final String NEW_B_DESCRIPTOR = "LB;";

  private static List<byte[]> programClassFileData;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    programClassFileData =
        ImmutableList.of(
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(descriptor(B.class), NEW_B_DESCRIPTOR)
                .transform(),
            transformer(B.class).setClassDescriptor(NEW_B_DESCRIPTOR).transform());
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(A.class)
        .addProgramClassFileData(programClassFileData)
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V5_1_1),
            TestRunResult::assertSuccessWithEmptyOutput,
            runResult -> runResult.assertFailureWithErrorThatThrows(IllegalAccessError.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class)
        .addProgramClassFileData(programClassFileData)
        .addKeepMainRule(Main.class)
        .allowAccessModification()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/182129249): Member rebinding does not preserve IAE.
        .assertSuccessWithEmptyOutput();
  }

  static class Main {

    public static void main(String[] args) {
      B.m();
    }
  }

  public static class A {

    public static void m() {}
  }

  static class /*different_package.*/ B extends A {}
}
