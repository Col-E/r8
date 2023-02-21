// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
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
public class InvokeSpecialOnSameClassRecursionTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSpecialOnSameClassRecursionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(Main.class, B.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo -1");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, B.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.foo -1");
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(A.class)
        .transformMethodInsnInMethod(
            "foo",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("foo")) {
                continuation.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  static class A {
    private int i = 5;

    public void foo() {
      if (i-- > 0) {
        foo();
      } else {
        System.out.println("A.foo " + i);
      }
    }
  }

  static class B extends A {
    public void run() {
      super.foo();
    }

    public void foo() {
      throw new RuntimeException("never called");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B().run();
    }
  }
}
