// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets;

import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidResolutionToThisTarget extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvalidResolutionToThisTarget(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AssertionError foo =
        assertThrows(
            AssertionError.class,
            () -> {
              AppView<AppInfoWithLiveness> appView =
                  computeAppViewWithLiveness(
                      buildClasses(A.class)
                          .addClassProgramData(getMainWithModifiedReceiverCall())
                          .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                          .build(),
                      Main.class);
              AppInfoWithLiveness appInfo = appView.appInfo();
              DexMethod method = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
              MethodResolutionResult resolutionResult =
                  appInfo.resolveMethodOnClassHolderLegacy(method);
              assertTrue(resolutionResult.isSingleResolution());
              DexType mainType = buildType(Main.class, appInfo.dexItemFactory());
              DexProgramClass main = appView.definitionForProgramType(mainType);
              assertNull(resolutionResult.lookupVirtualDispatchTarget(main, appInfo));
            });
    assertThat(
        foo.getMessage(),
        containsString(Main.class.getTypeName() + " is not a subtype of " + A.class.getTypeName()));
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addProgramClasses(A.class)
        .addProgramClassFileData(getMainWithModifiedReceiverCall())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("java.lang.VerifyError"));
  }

  @Test
  public void testR8() {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(A.class)
                .addProgramClassFileData(getMainWithModifiedReceiverCall())
                .setMinApi(parameters)
                .addKeepMainRule(Main.class)
                .compileWithExpectedDiagnostics(
                    diagnosticMessages -> {
                      diagnosticMessages.assertErrorMessageThatMatches(
                          containsString(
                              "The receiver lower bound does not match the receiver type"));
                    }));
  }

  private byte[] getMainWithModifiedReceiverCall() throws IOException {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("foo")) {
                continuation.visitMethodInsn(
                    opcode,
                    DescriptorUtils.getBinaryNameFromJavaType(A.class.getTypeName()),
                    name,
                    descriptor,
                    isInterface);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public static class A {

    public void foo() {
      System.out.println("A.foo");
    }
  }

  public static class Main {

    public void foo() {
      System.out.println("Main.foo");
      new A().foo();
    }

    public static void main(String[] args) {
      new Main().foo();
    }
  }
}
