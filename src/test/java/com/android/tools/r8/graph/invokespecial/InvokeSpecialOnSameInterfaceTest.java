// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialOnSameInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSpecialOnSameInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello World!");
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(AInterface.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  public interface AInterface {
    default void foo() {
      System.out.println("Hello World!");
    }

    default void bar() {
      foo(); // Will be rewritten to invoke-special AInterface.foo()
    }
  }

  public static class A implements AInterface {}

  public static class Main {

    public static void main(String[] args) {
      new A().bar();
    }
  }
}
