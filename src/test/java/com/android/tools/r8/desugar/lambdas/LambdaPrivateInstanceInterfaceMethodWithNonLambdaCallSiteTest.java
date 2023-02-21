// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.lambdas;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.CfVersion;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class LambdaPrivateInstanceInterfaceMethodWithNonLambdaCallSiteTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  public LambdaPrivateInstanceInterfaceMethodWithNonLambdaCallSiteTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, A.class, FunctionalInterface.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!", "Hello world!")
        .inspect(
            inspector -> {
              if (parameters.isDexRuntime()
                  && !parameters.canUseDefaultAndStaticInterfaceMethods()) {
                inspector
                    .clazz(I.class)
                    .toCompanionClass()
                    .forAllMethods(
                        m ->
                            // We don't expect any synthetic accessors to be needed for the private
                            // interface method.
                            assertFalse("Unexpected synthetic method: " + m, m.isSynthetic()));
              }
            });
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, FunctionalInterface.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!", "Hello world!");
  }

  private byte[] getProgramClassFileData() throws IOException, NoSuchMethodException {
    return transformer(I.class)
        .transformInvokeDynamicInsnInMethod(
            "test",
            (name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments, visitor) -> {
              visitor.visitInvokeDynamicInsn(
                  name,
                  descriptor,
                  bootstrapMethodHandle,
                  bootstrapMethodArguments.get(0),
                  new Handle(
                      Opcodes.H_INVOKESPECIAL, binaryName(I.class), "privateMethod", "()V", true),
                  bootstrapMethodArguments.get(2));
            })
        .transformMethodInsnInMethod(
            "test",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("privateMethod")) {
                assert opcode == Opcodes.INVOKEINTERFACE;
                visitor.visitMethodInsn(
                    Opcodes.INVOKESPECIAL, owner, name, descriptor, isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .setPrivate(I.class.getDeclaredMethod("privateMethod"))
        .setVersion(CfVersion.V9)
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      new A().test();
    }
  }

  interface I {

    default void test() {
      FunctionalInterface f = this::privateMethod;
      f.m();
      privateMethod();
    }

    default void privateMethod() {
      System.out.println("Hello world!");
    }
  }

  static class A implements I {}

  interface FunctionalInterface {

    void m();
  }
}
