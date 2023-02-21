// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromJavaType;
import static org.junit.Assert.assertEquals;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

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
public class InvokeSpecialInterfaceWithBridge2Test extends TestBase {

  private static final String EXPECTED = StringUtils.lines("Hello World!");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSpecialInterfaceWithBridge2Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(I.class, A.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, A.class, Main.class)
        .addProgramClassFileData(getClassWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(B.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(INVOKEVIRTUAL, opcode);
              assertEquals(owner, getBinaryNameFromJavaType(B.class.getTypeName()));
              continuation.visitMethodInsn(
                  INVOKESPECIAL,
                  getBinaryNameFromJavaType(A.class.getTypeName()),
                  name,
                  descriptor,
                  isInterface);
            })
        .transform();
  }

  public interface I {
    default void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class A implements I {}

  public static class B extends A {

    public void bar() {
      foo(); // Will be rewritten to invoke-special A.foo()
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B().bar();
    }
  }
}
