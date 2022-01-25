// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InvokeSuperChainTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    // This tests shows that invoke super behaves differently on V5_1_1 and V6_0_1 based on the
    // static receiver on invoke-super, skipping the direct resolution target.
    boolean hasIncorrectSuperInvokeSemantics =
        parameters.isDexRuntimeVersion(Version.V5_1_1)
            || parameters.isDexRuntimeVersion(Version.V6_0_1);
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class)
        .addProgramClassFileData(
            rewriteBarSuperInvokeToA(B.class), rewriteBarSuperInvokeToA(C.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(
            !hasIncorrectSuperInvokeSemantics,
            "C::foo",
            "B::foo",
            "A::foo",
            "C::bar",
            "B::bar",
            "A::bar")
        .assertSuccessWithOutputLinesIf(
            hasIncorrectSuperInvokeSemantics, "C::foo", "B::foo", "A::foo", "C::bar", "A::bar");
  }

  private byte[] rewriteBarSuperInvokeToA(Class<?> clazz) throws Exception {
    return transformer(clazz)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (!name.equals("println")) {
                Assert.assertEquals(Opcodes.INVOKESPECIAL, opcode);
                Assert.assertEquals("bar", name);
                visitor.visitMethodInsn(opcode, binaryName(A.class), name, descriptor, isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public static class A {

    public void foo() {
      System.out.println("A::foo");
    }

    public void bar() {
      System.out.println("A::bar");
    }
  }

  public static class B extends A {

    @Override
    public void foo() {
      System.out.println("B::foo");
      super.foo();
    }

    @Override
    public void bar() {
      System.out.println("B::bar");
      super.bar();
    }
  }

  public static class C extends B {

    @Override
    public void foo() {
      System.out.println("C::foo");
      super.foo();
    }

    @Override
    public void bar() {
      System.out.println("C::bar");
      super.bar();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new C().foo();
      new C().bar();
    }
  }
}
