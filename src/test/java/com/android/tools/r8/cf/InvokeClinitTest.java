// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeClinitTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevel(AndroidApiLevel.B)
        .enableApiLevelsForCf()
        .build();
  }

  public InvokeClinitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(A.class)
        .addProgramClassFileData(transformMain())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(
            anyOf(
                containsString(ClassFormatError.class.getSimpleName()),
                containsString(VerifyError.class.getSimpleName())));
  }

  @Test
  public void testD8() {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(parameters.getBackend())
                .addProgramClasses(A.class)
                .addProgramClassFileData(transformMain())
                .setMinApi(parameters)
                .compile());
  }

  @Test
  public void testR8() {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(A.class)
                .addProgramClassFileData(transformMain())
                .addKeepMainRule(Main.class)
                .setMinApi(parameters)
                .compile());
  }

  private byte[] transformMain() throws IOException {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, continuation) ->
                continuation.visitMethodInsn(opcode, owner, "<clinit>", descriptor, isInterface))
        .transform();
  }

  static class A {
    static {
      System.out.println("A.<clinit>");
    }

    static void willBeClinit() {
      System.out.println("unused");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.willBeClinit();
    }
  }
}
