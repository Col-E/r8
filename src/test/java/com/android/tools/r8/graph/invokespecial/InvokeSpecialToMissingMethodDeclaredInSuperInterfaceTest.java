// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialToMissingMethodDeclaredInSuperInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSpecialToMissingMethodDeclaredInSuperInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(A.class, C.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo()");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, C.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo()");
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(B.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(INVOKEVIRTUAL, opcode);
              assertEquals("notify", name);
              continuation.visitMethodInsn(
                  INVOKESPECIAL, binaryName(B.class), "foo", descriptor, true);
            })
        .transform();
  }

  public interface A {

    default void foo() {
      System.out.println("A.foo()");
    }
  }

  public interface B extends A {

    default void bar() {
      notify(); // Will be rewritten to invoke-special B.foo() which is missing, but found in A.
    }
  }

  static class C implements B {}

  public static class Main {

    public static void main(String[] args) {
      new C().bar();
    }
  }
}
