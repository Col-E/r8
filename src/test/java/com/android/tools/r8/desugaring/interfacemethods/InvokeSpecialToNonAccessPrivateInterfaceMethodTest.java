// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InvokeSpecialToNonAccessPrivateInterfaceMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public InvokeSpecialToNonAccessPrivateInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(TestClass.class, A.class)
        .addProgramClassFileData(getTransformedI())
        .addProgramClassFileData(getTransformedJ())
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
  }

  private byte[] getTransformedI() throws Exception {
    return transformer(I.class).setPrivate(I.class.getMethod("foo")).transform();
  }

  private byte[] getTransformedJ() throws Exception {
    return transformer(J.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              assertEquals("foo", name);
              visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class, A.class)
        .addProgramClassFileData(getTransformedI())
        .addProgramClassFileData(getTransformedJ())
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
  }

  interface I {

    default /* will be private */ void foo() {
      System.out.println("Called I.foo");
    }
  }

  interface J extends I {

    default void bar() {
      foo(); // will be invoke-special
    }
  }

  static class A implements J {}

  static class TestClass {

    public static void main(String[] args) {
      new A().bar();
    }
  }
}
