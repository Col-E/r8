// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.hoisting;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BridgeToPackagePrivateMethodTest extends TestBase {

  private static final List<String> EXPECTED_OUTPUT = ImmutableList.of("A", "B");
  private static final String TRANSFORMED_A_DESCRIPTOR = "LA;";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private List<byte[]> getProgramClassFileData() throws IOException, NoSuchMethodException {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(A.class), TRANSFORMED_A_DESCRIPTOR)
            .transform(),
        transformer(A.class).setClassDescriptor(TRANSFORMED_A_DESCRIPTOR).transform(),
        transformer(B.class)
            .setSuper(TRANSFORMED_A_DESCRIPTOR)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(A.class), TRANSFORMED_A_DESCRIPTOR)
            .setBridge(B.class.getDeclaredMethod("bridge"))
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      new A().callM();
      new B().bridge();
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class /*otherpackage.*/ A {

    @NeverInline
    void m() {
      System.out.println("A");
    }

    @NeverInline
    public void callM() {
      m();
    }
  }

  @NeverClassInline
  static class B extends A {

    @NeverInline
    void m() {
      System.out.println("B");
    }

    // Not eligible for bridge hoisting, as the bridge will then dispatch to A.m instead of B.m.
    @NeverInline
    public /*bridge*/ void bridge() {
      this.m();
    }
  }
}
