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

// This is a reproduction of b/144450911.
@RunWith(Parameterized.class)
public class InvokeSpecialForNonDeclaredInvokeVirtualTest extends TestBase {

  private static final String EXPECTED = StringUtils.lines("Hello World!");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSpecialForNonDeclaredInvokeVirtualTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(A.class, B.class, Main.class)
        .addProgramClassFileData(getClassCWithTransformedInvoked())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, Main.class)
        .addProgramClassFileData(getClassCWithTransformedInvoked())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private byte[] getClassCWithTransformedInvoked() throws IOException {
    return transformer(C.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              assertEquals(INVOKEVIRTUAL, opcode);
              assertEquals(getBinaryNameFromJavaType(C.class.getTypeName()), owner);
              continuation.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  public static class A {

    void foo() {
      System.out.println("Hello World!");
    }
  }

  public static class B extends A {}

  public static class C extends B {

    void bar() {
      foo(); // Will be invoke-special C::foo
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new C().bar();
    }
  }
}
