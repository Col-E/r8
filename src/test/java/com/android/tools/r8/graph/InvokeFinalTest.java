// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import static org.junit.Assert.assertTrue;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeFinalTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeFinalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testCallingFinal()
      throws IOException, CompilationFailedException, ExecutionException {
    boolean hasIncorrectSuperLookup =
        parameters.isDexRuntime()
            && parameters.getRuntime().asDex().getVm().isNewerThan(DexVm.ART_4_4_4_HOST)
            && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_6_0_1_HOST);
    TestRunResult<?> runResult =
        testForRuntime(parameters)
            .addProgramClasses(Main.class, A.class)
            .addProgramClassFileData(
                getClassWithTransformedInvoked(B.class), getClassWithTransformedInvoked(C.class))
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines(
                "Hello from B",
                "Hello from B",
                hasIncorrectSuperLookup ? "Hello from A" : "Hello from B",
                "Hello from B",
                "Hello from A",
                "Hello from B");
  }

  private byte[] getClassWithTransformedInvoked(Class<?> clazz) throws IOException {
    return transformer(clazz)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              // The super call to bar() is already INVOKESPECIAL.
              assertTrue(name.equals("foo") || opcode == INVOKESPECIAL);
              continuation.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  public static class A {

    public void foo() {
      System.out.println("Hello from A");
    }

    public void bar() {
      // TODO(b/110175213): We cannot change this to an invoke-special since this requires a
      //  direct bridge in DEX.
      foo();
    }
  }

  public static class B extends A {

    // Having a final method allows us to rewrite invoke-special foo() to invoke-virtual foo().
    public final void foo() {
      System.out.println("Hello from B");
    }

    public void bar() {
      foo();
      ((A) this).foo();
      super.bar();
    }
  }

  public static class C extends B {

    public void bar() {
      foo();
      ((B) this).foo();
      ((A) this).foo();
      super.bar();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new C().bar();
    }
  }
}
