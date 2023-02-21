// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MultipleInvokeSpecialOnSameClassTest extends TestBase {

  private static final String EXPECTED_RESULT =
      StringUtils.lines("print", "print2", "print", "print2", "print", "print2");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MultipleInvokeSpecialOnSameClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(A.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transformMethodInsnInMethod(
            "baz",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  public static class A {

    public void print() {
      System.out.println("print");
    }

    public void print2() {
      System.out.println("print2");
    }

    public void bar() {
      print(); // Will be rewritten to invoke-special A.print()
      print2(); // Will be rewritten to invoke-special A.print2()
      print(); // Will be rewritten to invoke-special A.print()
      print2(); // Will be rewritten to invoke-special A.print2()
    }

    public void baz() {
      print(); // Will be rewritten to invoke-special A.print()
      print2(); // Will be rewritten to invoke-special A.print2()
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A a = new A();
      a.bar();
      a.baz();
    }
  }
}
