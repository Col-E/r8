// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.repackage.testclasses.CrossPackageInvokeSuperToPackagePrivateMethodTestClasses;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class CrossPackageInvokeSuperToPackagePrivateMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public CrossPackageInvokeSuperToPackagePrivateMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .apply(this::addProgramClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("A", "B", "A", "C", "D", "C");
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .apply(this::addProgramClasses)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .apply(this::inspectRunResult);
  }

  private void addProgramClasses(TestBuilder<?, ?> builder) throws IOException {
    builder
        .addProgramClasses(
            TestClass.class,
            A.class,
            B.class,
            CrossPackageInvokeSuperToPackagePrivateMethodTestClasses.C.class)
        .addProgramClassFileData(
            transformer(D.class)
                .transformMethodInsnInMethod(
                    "packagePrivate",
                    (opcode, owner, name, descriptor, isInterface, continuation) ->
                        continuation.visitMethodInsn(
                            name.equals("packagePrivate") ? Opcodes.INVOKESPECIAL : opcode,
                            name.equals("packagePrivate")
                                ? binaryName(
                                        CrossPackageInvokeSuperToPackagePrivateMethodTest.class)
                                    + "$B"
                                : owner,
                            name,
                            descriptor,
                            isInterface))
                .transform());
  }

  private void inspectRunResult(R8TestRunResult runResult) {
    if (parameters.isCfRuntime()
        || parameters.getRuntime().asDex().getVm().getVersion().isDalvik()) {
      runResult.assertSuccessWithOutputLines("A", "B", "A", "C", "D", "C");
    } else {
      runResult.assertSuccessWithOutputLines("A", "B", "A", "C", "D", "B", "A");
      if (parameters.getRuntime().asDex().getVm().getVersion().isOlderThanOrEqual(Version.V7_0_0)) {
        runResult.assertStderrMatches(
            allOf(
                containsString("Before Android 4.1, method"),
                containsString("would have incorrectly overridden the package-private method")));
      }
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().packagePrivate();
      new B().packagePrivate();
      new CrossPackageInvokeSuperToPackagePrivateMethodTestClasses.C().runPackagePrivate();
      new D().packagePrivate();
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class A {

    @NeverInline
    void packagePrivate() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class B extends A {

    @NeverInline
    void packagePrivate() {
      System.out.println("B");
      super.packagePrivate();
    }
  }

  @NeverClassInline
  public static class D extends CrossPackageInvokeSuperToPackagePrivateMethodTestClasses.C {

    @NeverInline
    void packagePrivate() {
      System.out.println("D");
      /*super.*/packagePrivate();
    }
  }
}
